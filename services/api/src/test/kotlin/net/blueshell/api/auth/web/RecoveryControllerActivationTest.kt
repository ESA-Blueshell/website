package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.RecoveryUseCases
import net.blueshell.api.shared.model.SignupOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RecoveryControllerActivationTest {
    private val useCases: RecoveryUseCases = mock()
    private val controller = RecoveryController(useCases, mock())

    @Test
    fun `an activation says whether a membership started and whether the next run collects its first contribution`() {
        val outcome = SignupOutcome(emailConfirmed = true, membershipStarted = true, collectedByIncasso = true)
        whenever(useCases.activateUser("tok")).thenReturn(outcome)

        val response = controller.userActivate(UserActivationRequest("tok"))

        assertThat(response.membershipStarted).isTrue()
        assertThat(response.collectedByIncasso).isTrue()
    }
}
