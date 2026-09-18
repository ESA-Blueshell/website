package net.blueshell.api.auth.domain

import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.SignupDetailsData
import net.blueshell.api.user.api.UpsertMemberProfileData
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.sql.Date
import java.time.LocalDate

class SignupWriteUseCasesTest {
    private companion object {
        const val APPLICANT_ID = 7L
    }

    private val users = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val signupTokens = mock<SignupTokenService>()
    private val completion = mock<SignupCompletionService>()
    private val activation = mock<UserActivationService>()
    private val jobs = mock<JobQueue>()

    private val useCases = SignupUseCases(signupTokens, users, memberProfiles, completion, activation, jobs)

    private fun applicant(withProfile: Boolean): User {
        val user =
            User(
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
        private fun save(houseNumber: String = "5") =
            useCases.saveAddress(
                signupToken = "sel.ver",
                country = "NL",
                city = "Enschede",
                street = "Drienerlolaan",
                houseNumber = houseNumber,
                zipCode = "7522NB",
            )

        @Test
        fun `attaches the address to the account the token speaks for`() {
            val user = applicant(withProfile = true)

            save()

            assertThat(user.address).isNotNull()
            assertThat(user.address!!.houseNumber).isEqualTo("5")
            verify(users).update(user)
        }

        @Test
        fun `replaces an address that is already on file`() {
            val user = applicant(withProfile = true)
            save()

            save(houseNumber = "7")

            assertThat(user.address!!.houseNumber).isEqualTo("7")
        }
    }

    @Nested
    inner class SubmitApplication {
        @Test
        fun `stamps the acceptance and reports the outcome`() {
            val user = applicant(withProfile = true)
            whenever(completion.completeIfReady(APPLICANT_ID))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = useCases.submitApplication("sel.ver")

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
            val outcome = useCases.submitApplication("sel.ver")

            assertThat(outcome.membershipStarted).isFalse()
        }

        @Test
        fun `refuses a signup that never asked for membership`() {
            applicant(withProfile = false)

            // Not an AccessDeniedException: that is translated outside the dispatch and
            // answers with no body, so the applicant was told they lacked authority.
            assertThatThrownBy { useCases.submitApplication("sel.ver") }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("did not apply for membership")
                .asInstanceOf(
                    org.assertj.core.api.InstanceOfAssertFactories
                        .type(ResponseStatusException::class.java),
                ).extracting { it.statusCode }
                .isEqualTo(HttpStatus.FORBIDDEN)
            verify(memberProfiles, never()).update(org.mockito.kotlin.any())
        }
    }

    @Nested
    inner class CorrectEmail {
        @Test
        fun `records the new address and sends the confirmation link after it`() {
            val user = applicant(withProfile = false)
            whenever(users.existsByEmailAndIdNot("corrected@example.com", APPLICANT_ID)).thenReturn(false)
            whenever(activation.requestUserActivation("applicant"))
                .thenReturn(RecoveryDispatch(APPLICANT_ID, "new.token", TokenPurpose.USER_ACTIVATION))

            useCases.correctEmail("sel.ver", "corrected@example.com")

            assertThat(user.email).isEqualTo("corrected@example.com")
            verify(users).update(user)
            verify(jobs).runAsync(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(APPLICANT_ID, "new.token", TokenPurpose.USER_ACTIVATION),
            )
        }

        // Once the address is confirmed it identifies the account, so changing it is a
        // signed-in decision rather than something a signup token may still do.
        @Test
        fun `refuses to change an address that was already confirmed`() {
            val user = applicant(withProfile = false)
            user.enabled = true

            assertThatThrownBy { useCases.correctEmail("sel.ver", "corrected@example.com") }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("account settings")
            assertThat(user.email).isEqualTo("applicant@example.com")
            verify(users, never()).update(org.mockito.kotlin.any())
        }

        @Test
        fun `refuses an address that belongs to somebody else`() {
            applicant(withProfile = false)
            whenever(users.existsByEmailAndIdNot("taken@example.com", APPLICANT_ID)).thenReturn(true)

            assertThatThrownBy { useCases.correctEmail("sel.ver", "taken@example.com") }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("already in use")
            verifyNoInteractions(activation)
        }

        // The account is unconfirmed by construction here, so a null dispatch means the
        // activation service disagreed about that and the signup must not carry on.
        @Test
        fun `refuses when no confirmation link could be issued`() {
            applicant(withProfile = false)
            whenever(activation.requestUserActivation("applicant")).thenReturn(null)

            assertThatThrownBy { useCases.correctEmail("sel.ver", "corrected@example.com") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("activation dispatch")
            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class UpdateDetails {
        private fun details(
            username: String = "applicant",
            profile: UpsertMemberProfileData? = null,
        ) = SignupDetailsData(
            username = username,
            initials = "AP",
            firstName = "App",
            prefix = "van",
            lastName = "Licant",
            discord = "applicant#0001",
            phoneNumber = "0612345678",
            newsletter = true,
            photoConsent = true,
            memberProfile = profile,
        )

        @Test
        fun `writes the corrected details onto the account the token speaks for`() {
            val user = applicant(withProfile = false)

            useCases.updateDetails("sel.ver", details(username = "corrected"))

            assertThat(user.username).isEqualTo("corrected")
            assertThat(user.initials).isEqualTo("AP")
            assertThat(user.prefix).isEqualTo("van")
            assertThat(user.newsletter).isTrue()
            assertThat(user.photoConsent).isTrue()
            verify(users).update(user)
        }

        @Test
        fun `starts a profile the first step did not send`() {
            val user = applicant(withProfile = false)

            useCases.updateDetails(
                "sel.ver",
                details(
                    profile =
                        UpsertMemberProfileData(
                            dateOfBirth = Date.valueOf(LocalDate.of(2000, 1, 2)),
                            studentNumber = "s1234567",
                            gender = "female",
                            nationality = "NL",
                            bhv = true,
                            ehbo = false,
                            nameOnRosters = true,
                        ),
                ),
            )

            assertThat(user.memberProfile?.nationality).isEqualTo("NL")
            assertThat(user.memberProfile?.nameOnRosters).isTrue()
        }

        @Test
        fun `refuses a confirmed account`() {
            val user = applicant(withProfile = false)
            user.enabled = true

            assertThatThrownBy { useCases.updateDetails("sel.ver", details(username = "corrected")) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("under a session")
            assertThat(user.username).isEqualTo("applicant")
        }

        // Uniqueness is asserted per field so the applicant is told which one collided;
        // a single "details are taken" would leave them guessing.
        @Test
        fun `refuses a username somebody else holds`() {
            applicant(withProfile = false)
            whenever(users.existsByUsernameAndIdNot("applicant", APPLICANT_ID)).thenReturn(true)

            assertThatThrownBy { useCases.updateDetails("sel.ver", details()) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("username is already in use")
            verify(users, never()).update(org.mockito.kotlin.any())
        }

        @Test
        fun `refuses a Discord name somebody else holds`() {
            applicant(withProfile = false)
            whenever(users.existsByDiscordAndIdNot("applicant#0001", APPLICANT_ID)).thenReturn(true)

            assertThatThrownBy { useCases.updateDetails("sel.ver", details()) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Discord name is already in use")
        }

        @Test
        fun `refuses a phone number somebody else holds`() {
            applicant(withProfile = false)
            whenever(users.existsByPhoneNumberAndIdNot("0612345678", APPLICANT_ID)).thenReturn(true)

            assertThatThrownBy { useCases.updateDetails("sel.ver", details()) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("phone number is already in use")
        }
    }
}
