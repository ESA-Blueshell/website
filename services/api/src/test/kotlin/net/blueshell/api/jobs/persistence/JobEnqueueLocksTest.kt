package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.enums.JobExecutionStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class JobEnqueueLocksTest {
    private val granted: ResultSet =
        mock {
            on { next() } doReturn true
            on { getInt(1) } doReturn 1
        }
    private val twins: ResultSet = mock()
    private val lock: PreparedStatement = mock { on { executeQuery() } doReturn granted }
    private val read: PreparedStatement = mock { on { executeQuery() } doReturn twins }
    private val release: PreparedStatement = mock()

    // The twin read is any other statement; the two lock statements are stubbed after it, so they win.
    private val connection: Connection = mock { on { prepareStatement(any<String>()) } doReturn read }
    private val dataSource: DataSource = mock { on { this.connection } doReturn connection }
    private val locks = JobEnqueueLocks(dataSource)

    init {
        whenever(connection.prepareStatement("SELECT GET_LOCK(?, ?)")).thenReturn(lock)
        whenever(connection.prepareStatement("SELECT RELEASE_ALL_LOCKS()")).thenReturn(release)
    }

    @AfterEach
    fun clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
        TransactionSynchronizationManager.getResourceMap().keys.forEach(TransactionSynchronizationManager::unbindResource)
    }

    @Test
    fun `takes the lock and reads the twins on one connection of the transaction's, and gives them back as it ends`() {
        TransactionSynchronizationManager.initSynchronization()
        whenever(twins.next()).thenReturn(true, true, false)
        whenever(twins.getLong(1)).thenReturn(3, 4)
        whenever(twins.getString(2)).thenReturn("RUNNING", "QUEUED")

        assertThat(locks.lock("demo", "k", 10)).isTrue()
        assertThat(locks.activeTwins("demo", "k"))
            .containsExactly(ActiveTwin(3, JobExecutionStatus.RUNNING), ActiveTwin(4, JobExecutionStatus.QUEUED))

        verify(dataSource, times(1)).connection
        val name = argumentCaptor<String>()
        verify(lock).setString(eq(1), name.capture())
        assertThat(name.firstValue).startsWith("job-enqueue-").hasSizeLessThanOrEqualTo(64)
        TransactionSynchronizationManager.getSynchronizations().single().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        verify(release).execute()
        verify(connection).close()
        assertThat(TransactionSynchronizationManager.hasResource(locks)).isFalse()
    }

    @Test
    fun `says where another enqueue kept the lock`() {
        TransactionSynchronizationManager.initSynchronization()
        whenever(granted.getInt(1)).thenReturn(0)

        assertThat(locks.lock("demo", "k", 10)).isFalse()
    }

    @Test
    fun `refuses to lock outside a transaction, which would never give the lock back`() {
        assertThatThrownBy { locks.lock("demo", "k", 10) }.hasMessageContaining("inside a transaction")
    }
}
