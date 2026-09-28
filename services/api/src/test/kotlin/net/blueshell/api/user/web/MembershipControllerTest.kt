package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.user.domain.MembershipUseCases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipControllerTest {
    private val useCases = mock<MembershipUseCases>()
    private val controller = MembershipController(mock(), useCases)

    @Test
    fun `applies for the signed-in account with the acceptance the request carries`() {
        val principal = UserPrincipal(7, "guest", "h", true, setOf(Role.GUEST), 3, null)
        whenever(useCases.apply(7, conditionsAccepted = true))
            .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

        val response = controller.createMembership(MembershipApplicationRequest(conditionsAccepted = true), principal)

        assertThat(response.emailConfirmed).isTrue()
        assertThat(response.membershipStarted).isTrue()
    }
}
