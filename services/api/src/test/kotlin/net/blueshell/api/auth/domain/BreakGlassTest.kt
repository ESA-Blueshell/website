package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class BreakGlassTest {
    private val users = mock<UserService>()
    private val accountSecurity = mock<AccountSecurity>()
    private val recovery = mock<RecoveryUseCases>()
    private val events = mock<SecurityEvents>()
    private val breakGlass = BreakGlass(users, accountSecurity, recovery, events)
    private val admin =
        User(username = "root", email = "root@example.com", password = "h", initials = "R", firstName = "R", lastName = "Oot").also {
            it.id = 1
            it.lockedAt = Instant.EPOCH
        }

    @BeforeEach
    fun setUp() {
        whenever(users.findByUsername("root")).thenReturn(admin)
    }

    @Test
    fun `unlocking clears the lock, sends a password reset and tells every admin`() {
        breakGlass.run(BreakGlassAction.UNLOCK, "root", "the last admin, in person")

        assertThat(admin.lockedAt).isNull()
        verify(users).update(admin)
        verify(recovery).resetPassword("root")
        verify(events).record(eq(1L), eq(SecurityEventKind.ACCOUNT_UNLOCKED), eq(SecurityActor.Operator), any(), anyOrNull(), anyOrNull())
        verify(events).record(
            eq(1L),
            eq(SecurityEventKind.BREAK_GLASS),
            eq(SecurityActor.Operator),
            eq("UNLOCK: the last admin, in person"),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a reset goes through the same reset an admin performs, for the operator`() {
        breakGlass.run(BreakGlassAction.RESET_TWO_FACTOR, "root", "lost everything")

        verify(accountSecurity).resetTwoFactor(1, SecurityActor.Operator, "lost everything")
    }

    @Test
    fun `a run needs a reason`() {
        assertThrows<IllegalArgumentException> { breakGlass.run(BreakGlassAction.UNLOCK, "root", " ") }
    }
}
