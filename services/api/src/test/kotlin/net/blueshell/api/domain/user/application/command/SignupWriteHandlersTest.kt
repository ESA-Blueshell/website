package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.domain.auth.application.SignupTokenService
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.SaveSignupAddressCommand
import net.blueshell.api.domain.user.command.SubmitSignupApplicationCommand
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.auth.application.SignupAccount
import net.blueshell.api.shared.model.SignupOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException

class SignupWriteHandlersTest {

    private companion object {
        const val APPLICANT_ID = 7L
    }

    private val users = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val signupTokens = mock<SignupTokenService>()
    private val completion = mock<SignupCompletionService>()

    private fun applicant(withProfile: Boolean): User {
        val user = User(
            username = "applicant",
            email = "applicant@example.com",
            password = "encoded",
            initials = "AP",
            firstName = "App",
            prefix = null,
            lastName = "Licant",
            phoneNumber = "0612345678",
            discord = "applicant#0001",
            newsletter = false,
        )
        user.id = APPLICANT_ID
        if (withProfile) {
            user.replaceMemberProfile(MemberProfile(user = user, bhv = false, ehbo = false))
        }
        whenever(signupTokens.resolveAccount("sel.ver")).thenReturn(SignupAccount(APPLICANT_ID, user))
        return user
    }

    @Nested
    inner class SaveAddress {

        private val handler = SaveSignupAddressHandler(users, signupTokens)

        private fun command() = SaveSignupAddressCommand(
            signupToken = "sel.ver",
            country = "NL",
            city = "Enschede",
            street = "Drienerlolaan",
            houseNumber = "5",
            zipCode = "7522NB",
        )

        @Test
        fun `attaches the address to the account the token speaks for`() {
            val user = applicant(withProfile = true)

            handler.handle(command())

            assertThat(user.address).isNotNull()
            assertThat(user.address!!.houseNumber).isEqualTo("5")
            verify(users).update(user)
        }

        @Test
        fun `replaces an address that is already on file`() {
            val user = applicant(withProfile = true)
            handler.handle(command())

            handler.handle(command().copy(houseNumber = "7"))

            assertThat(user.address!!.houseNumber).isEqualTo("7")
        }
    }

    @Nested
    inner class SubmitApplication {

        private val handler = SubmitSignupApplicationHandler(memberProfiles, signupTokens, completion)

        private fun command() = SubmitSignupApplicationCommand("sel.ver", conditionsAccepted = true)

        @Test
        fun `stamps the acceptance and reports the outcome`() {
            val user = applicant(withProfile = true)
            whenever(completion.completeIfReady(APPLICANT_ID))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = handler.handle(command())

            assertThat(user.memberProfile!!.conditionsAcceptedAt).isNotNull()
            verify(memberProfiles).update(user.memberProfile!!)
            assertThat(outcome.membershipStarted).isTrue()
        }

        @Test
        fun `reports a non-commit rather than refusing`() {
            val user = applicant(withProfile = true)
            whenever(completion.completeIfReady(APPLICANT_ID))
                .thenReturn(SignupOutcome(emailConfirmed = false, membershipStarted = false))

            // Unlike the signed-in route, not-yet-ready is the normal case here.
            val outcome = handler.handle(command())

            assertThat(outcome.membershipStarted).isFalse()
        }

        @Test
        fun `refuses a signup that never asked for membership`() {
            applicant(withProfile = false)

            assertThatThrownBy { handler.handle(command()) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("did not apply for membership")
            verify(memberProfiles, never()).update(org.mockito.kotlin.any())
        }
    }
}
