package net.blueshell.api.platform.integration.queue

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Per-aggregate serial-execution primitive for sync handlers.
 *
 * Implementations guarantee that two callers using the same [name] are mutually
 * excluded while the action runs. The lock is acquired with a bounded wait;
 * if it cannot be acquired within [timeoutSeconds] a [SyncLockBusyException]
 * is thrown so the calling job is rescheduled by the job framework's standard
 * retry path.
 *
 * Handlers must run inside an active transaction so the lock is held on the
 * transaction's pinned connection — otherwise Hibernate would borrow a fresh
 * connection per statement and the lock would be released by the pool before
 * the work completes.
 */
interface SyncLock {
    fun <T> withLock(name: String, timeoutSeconds: Int = 1, action: () -> T): T
}

class SyncLockBusyException(message: String) : RuntimeException(message)

/**
 * MariaDB-backed [SyncLock] using `GET_LOCK(name, timeout)` / `RELEASE_LOCK(name)`.
 * The lock is session-scoped: it must be acquired and released on the same JDBC
 * connection, which is guaranteed by running inside a Spring-managed transaction
 * that has bound a connection from the pool.
 */
@Component
@Profile("!test")
class MariaDbSyncLock(
    private val jdbcTemplate: JdbcTemplate,
) : SyncLock {
    override fun <T> withLock(name: String, timeoutSeconds: Int, action: () -> T): T {
        val acquired = jdbcTemplate.queryForObject(
            "SELECT GET_LOCK(?, ?)",
            Int::class.java,
            name,
            timeoutSeconds,
        )
        if (acquired != 1) {
            throw SyncLockBusyException("Could not acquire sync lock '$name' within ${timeoutSeconds}s")
        }
        try {
            return action()
        } finally {
            try {
                jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Int::class.java, name)
            } catch (e: Exception) {
                log.warn("Failed to release sync lock '{}': {}", name, e.message)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MariaDbSyncLock::class.java)
    }
}

/**
 * In-process [SyncLock] used in unit / integration tests where no MariaDB
 * session is available. Backed by per-name [ReentrantLock]s; mutual exclusion
 * is JVM-scoped only.
 */
@Component
@Profile("test")
class InMemorySyncLock : SyncLock {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun <T> withLock(name: String, timeoutSeconds: Int, action: () -> T): T {
        val lock = locks.computeIfAbsent(name) { ReentrantLock() }
        val acquired = lock.tryLock(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!acquired) {
            throw SyncLockBusyException("Could not acquire sync lock '$name' within ${timeoutSeconds}s")
        }
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
