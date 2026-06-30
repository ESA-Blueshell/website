package net.blueshell.api.domain.user.application.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.junit.jupiter.api.Test

class UserEventListenerTest {
    private val users: UserService = mockk()
    private val committeeMembers: CommitteeMemberService = mockk(relaxed = true)
    private val listener = UserEventListener(users, committeeMembers)

    @Test
    fun `committee authority satisfies member prerequisite and keeps committee memberships`() {
        val membership = mockk<CommitteeMember>()
        val user = mockk<User>()
        every { user.hasAuthority(Role.MEMBER) } returns true
        every { user.committeeMembers } returns setOf(membership)
        every { users.findById(42L) } returns user

        listener.onUpdate(UserUpdated(42L))

        verify(exactly = 0) { committeeMembers.delete(any<CommitteeMember>()) }
    }
}
