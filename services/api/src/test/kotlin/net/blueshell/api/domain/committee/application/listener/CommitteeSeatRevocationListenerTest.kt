package net.blueshell.api.domain.committee.application.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.junit.jupiter.api.Test

class CommitteeSeatRevocationListenerTest {
    private val users: UserService = mockk()
    private val committeeMembers: CommitteeMemberService = mockk(relaxed = true)
    private val listener = CommitteeSeatRevocationListener(users, committeeMembers)

    @Test
    fun `committee authority satisfies member prerequisite and keeps committee seats`() {
        val user = mockk<User>()
        every { user.hasAuthority(Role.MEMBER) } returns true
        every { users.findById(42L) } returns user

        listener.onUpdate(UserUpdated(42L))

        verify(exactly = 0) { committeeMembers.revokeAllSeatsForUser(any()) }
    }

    @Test
    fun `losing the member role gives up every seat the user holds`() {
        val user = mockk<User>()
        every { user.hasAuthority(Role.MEMBER) } returns false
        every { users.findById(42L) } returns user

        listener.onUpdate(UserUpdated(42L))

        verify(exactly = 1) { committeeMembers.revokeAllSeatsForUser(42L) }
    }
}
