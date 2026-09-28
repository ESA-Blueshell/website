package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.enums.JobExecutionStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.sql.Connection
import javax.sql.DataSource

/** A job's active twin as committed now: one of the same type and dedup key, queued or running. */
data class ActiveTwin(
    val id: Long,
    val status: JobExecutionStatus,
)

/**
 * Keeps two enqueues of one job type and dedup key from racing, across pods, until the enqueuing
 * transaction ends. The MariaDB named lock sits on a connection of its own, one per transaction,
 * given back once it completes: on the transaction's own connection it would have to go before the
 * commit, and a locking read over job_executions in its place gap-locks the table against the
 * executor's status updates, which deadlocked. That connection also reads the twins, every
 * statement seeing what is committed now rather than the transaction's snapshot.
 */
@Component
class JobEnqueueLocks(
    private val dataSource: DataSource,
) {
    /** Waits up to [seconds] for the lock on [jobType] and [dedupKey]; false where another enqueue kept it. */
    fun lock(
        jobType: String,
        dedupKey: String,
        seconds: Int,
    ): Boolean =
        connection().prepareStatement("SELECT GET_LOCK(?, ?)").use { statement ->
            statement.setString(1, nameOf(jobType, dedupKey))
            statement.setInt(2, seconds)
            statement.executeQuery().use { it.next() && it.getInt(1) == 1 }
        }

    fun activeTwins(
        jobType: String,
        dedupKey: String,
    ): List<ActiveTwin> =
        connection()
            .prepareStatement(
                "SELECT id, status FROM job_executions WHERE job_type = ? AND dedup_key = ? " +
                    "AND status IN ('QUEUED', 'RUNNING') ORDER BY id",
            ).use { statement ->
                statement.setString(1, jobType)
                statement.setString(2, dedupKey)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(ActiveTwin(rows.getLong(1), JobExecutionStatus.valueOf(rows.getString(2))))
                    }
                }
            }

    private fun connection(): Connection {
        (TransactionSynchronizationManager.getResource(this) as Connection?)?.let { return it }
        check(TransactionSynchronizationManager.isSynchronizationActive()) { "A job is enqueued inside a transaction" }
        val connection = dataSource.connection
        TransactionSynchronizationManager.bindResource(this, connection)
        TransactionSynchronizationManager.registerSynchronization(Release(this, connection))
        return connection
    }

    // The locks go explicitly: closing a pooled connection keeps its session, and with it the locks.
    private class Release(
        private val key: Any,
        private val connection: Connection,
    ) : TransactionSynchronization {
        override fun afterCompletion(status: Int) {
            TransactionSynchronizationManager.unbindResource(key)
            connection.use { it.prepareStatement("SELECT RELEASE_ALL_LOCKS()").use { statement -> statement.execute() } }
        }
    }

    private companion object {
        // MariaDB caps a lock's name at 64 characters.
        const val NAME_HEX = 40

        fun nameOf(
            jobType: String,
            dedupKey: String,
        ): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$jobType|$dedupKey".toByteArray())
            return "job-enqueue-" + digest.joinToString("") { "%02x".format(it) }.take(NAME_HEX)
        }
    }
}
