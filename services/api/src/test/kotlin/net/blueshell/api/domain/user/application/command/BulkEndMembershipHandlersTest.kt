package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BulkEndMembershipHandlersTest {

    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val handler = ExecuteBulkEndMembershipHandler(membershipService, userService)

    @Test
    fun `ends an active membership for a plain member`() {
        val membership = stubUser(1L, roles = setOf(Role.MEMBER), memberships = listOf(activeMembership()))

        val result = handler.handle(ExecuteBulkEndMembershipCommand(listOf(1L)))

        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).isZero()
        assertThat(membership.endDate).isEqualTo(LocalDate.now())
        verify(membershipService).update(membership)
    }

    @Test
    fun `protects every role above member`() {
        // The rule this covers is the reason bulk end-membership is safe to expose at
        // all: a mis-selected board member must keep their membership.
        setOf(Role.COMMITTEE, Role.BOARD, Role.TREASURER, Role.ADMIN).forEachIndexed { index, role ->
            val service = mock<MembershipService>()
            val users = mock<UserService>()
            val handler = ExecuteBulkEndMembershipHandler(service, users)
            val userId = (index + 1).toLong()
            whenever(users.existsById(userId)).thenReturn(true)
            whenever(users.findById(userId)).thenReturn(user(userId, setOf(role)))

            val result = handler.handle(ExecuteBulkEndMembershipCommand(listOf(userId)))

            assertThat(result.applied).describedAs("applied for %s", role).isZero()
            assertThat(result.skipped).describedAs("skipped for %s", role).isEqualTo(1)
            verify(service, never()).update(any())
        }
    }

    @Test
    fun `skips honorary members`() {
        stubUser(2L, roles = setOf(Role.MEMBER), memberships = listOf(activeMembership(MemberType.HONORARY)))

        val result = handler.handle(ExecuteBulkEndMembershipCommand(listOf(2L)))

        assertThat(result.applied).isZero()
        assertThat(result.skipped).isEqualTo(1)
        verify(membershipService, never()).update(any())
    }

    @Test
    fun `skips a membership that started today`() {
        stubUser(3L, roles = setOf(Role.MEMBER), memberships = listOf(activeMembership(startDate = LocalDate.now())))

        val result = handler.handle(ExecuteBulkEndMembershipCommand(listOf(3L)))

        assertThat(result.applied).isZero()
        assertThat(result.skipped).isEqualTo(1)
    }

    @Test
    fun `an unknown user id does not discard the rest of the batch`() {
        val membership = stubUser(4L, roles = setOf(Role.MEMBER), memberships = listOf(activeMembership()))
        whenever(userService.existsById(99L)).thenReturn(false)

        val result = handler.handle(ExecuteBulkEndMembershipCommand(listOf(99L, 4L)))

        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(membership.endDate).isEqualTo(LocalDate.now())
    }

    private fun stubUser(id: Long, roles: Set<Role>, memberships: List<Membership>): Membership {
        whenever(userService.existsById(id)).thenReturn(true)
        whenever(userService.findById(id)).thenReturn(user(id, roles))
        whenever(membershipService.findByQuery(MembershipQuery(userId = id))).thenReturn(memberships.toMutableList())
        return memberships.first()
    }

    private fun user(id: Long, roles: Set<Role>): User = User(
        username = "user$id",
        email = "user$id@example.com",
        password = "hash",
        initials = "U",
        firstName = "User$id",
        lastName = "",
    ).apply {
        setField(this, "id", id)
        this.roles = roles.toMutableSet()
    }

    private fun activeMembership(
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.of(2023, 1, 1),
    ): Membership = Membership(
        user = mock(),
        startDate = startDate,
        endDate = null,
        memberType = memberType,
        incasso = false,
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            runCatching {
                cls!!.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
            }.onSuccess { return }
            cls = cls.superclass
        }
        error("field $name not found on ${target.javaClass}")
    }
}
