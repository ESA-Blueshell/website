package net.blueshell.api.user.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.shared.enums.Role
import org.junit.jupiter.api.Test
import net.blueshell.api.user.api.MembershipChanged
import net.blueshell.api.user.api.UserService

class MembershipEventListenerTest {
    private val users: UserService = mockk(relaxed = true)
    private val listener = MembershipEventListener(users)

    @Test
    fun `closed historical membership does not remove member role while another active membership exists`() {
        every { users.existsActiveMembershipByUserId(7L) } returns true

        listener.onChange(MembershipChanged(7L, active = false, changeType = MembershipChange.UPDATED))

        verify(exactly = 0) { users.removeRole(7L, Role.MEMBER) }
    }

    @Test
    fun `last inactive membership removes member role when aggregate has no active membership`() {
        every { users.existsActiveMembershipByUserId(7L) } returns false

        listener.onChange(MembershipChanged(7L, active = false, changeType = MembershipChange.DELETED))

        verify { users.removeRole(7L, Role.MEMBER) }
    }
}
