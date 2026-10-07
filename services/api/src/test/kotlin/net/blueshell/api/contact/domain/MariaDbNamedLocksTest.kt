package net.blueshell.api.contact.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class MariaDbNamedLocksTest {
    private val answer = mock<ResultSet>()
    private val take = mock<PreparedStatement> { on { executeQuery() } doReturn answer }
    private val release = mock<PreparedStatement>()
    private val connection =
        mock<Connection> {
            on { prepareStatement("SELECT GET_LOCK(?, ?)") } doReturn take
            on { prepareStatement("SELECT RELEASE_LOCK(?)") } doReturn release
        }
    private val locks = MariaDbNamedLocks(mock<DataSource> { on { this.connection } doReturn connection })

    @Test
    fun `runs the block holding the lock, lets it go after, even when the block throws, and keeps a long name short`() {
        whenever(answer.next()).thenReturn(true)
        whenever(answer.getInt(1)).thenReturn(1)

        assertThat(locks.holding("brevo-folder:contributions") { 7 }).isEqualTo(7)
        assertThatThrownBy { locks.holding("x".repeat(80)) { error("Brevo said no") } }.hasMessage("Brevo said no")

        inOrder(take, release) {
            verify(take).setString(1, "brevo-folder:contributions")
            verify(release).setString(1, "brevo-folder:contributions")
            verify(take).setString(1, "x".repeat(64))
            verify(release).setString(1, "x".repeat(64))
        }
        verify(connection, org.mockito.kotlin.times(2)).close()
    }

    @Test
    fun `refuses to run the block when another replica keeps the lock`() {
        whenever(answer.next()).thenReturn(true)
        whenever(answer.getInt(1)).thenReturn(0)
        var ran = false

        assertThatThrownBy { locks.holding("brevo-folder:contributions") { ran = true } }.isInstanceOf(NamedLockTimeout::class.java)

        assertThat(ran).isFalse()
        verify(release, never()).setString(any(), any())
    }
}
