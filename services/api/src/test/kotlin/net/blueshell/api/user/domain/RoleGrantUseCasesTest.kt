package net.blueshell.api.user.domain

import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.RoleChangeRepository
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class RoleGrantUseCasesTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val users = mock<UserService>()
    private val roleChanges = mock<RoleChangeRepository>()
    private val grants = RoleGrantUseCases(
        users,
        roleChanges,
        mock { on { currentUser() } doReturn CurrentUser(1, setOf(Role.ADMIN), null) },
        mock(),
        clock,
    )

    private fun person(
        id: Long,
        vararg roles: Role,
    ) = User(
        username = "u$id",
        email = "u$id@example.com",
        password = "h",
        initials = "U",
        firstName = "U",
        lastName = "$id",
        roles = roles.toMutableSet(),
    ).also { it.id = id }

    @Test
    fun `a granted role without two-factor is held but dormant, and the change is dated by the clock`() {
        val subject = person(7, Role.MEMBER)
        whenever(users.findById(7)).thenReturn(subject)
        whenever(users.findById(1)).thenReturn(person(1, Role.ADMIN).also { it.twoFactorSince = Instant.EPOCH })
        whenever(users.update(subject)).thenReturn(subject)
        whenever(roleChanges.save(any<RoleChange>())).thenAnswer { (it.arguments[0] as RoleChange).also { change -> change.id = 3 } }

        val standing = grants.setGrantedRoles(7, setOf(Role.TREASURER), null)

        assertThat(standing.granted).containsExactly(Role.TREASURER)
        assertThat(standing.dormant).containsExactly(Role.TREASURER)
        val saved = argumentCaptor<RoleChange>()
        verify(roleChanges).save(saved.capture())
        assertThat(saved.firstValue.changedAt).isEqualTo(clock.instant())
        assertThat(grants.readRoles(7).dormant).containsExactly(Role.TREASURER)
    }
}
