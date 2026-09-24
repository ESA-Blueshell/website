package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.SignupResume
import net.blueshell.api.auth.domain.SignupResumeAddress
import net.blueshell.api.auth.domain.SignupResumeProfile
import net.blueshell.api.auth.domain.SignupUseCases
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.shared.model.SignupSession
import net.blueshell.api.user.api.SignupDetailsData
import net.blueshell.api.user.api.UserUseCases
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.web.CreateUserRequest
import net.blueshell.api.user.web.UpsertMemberProfileRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

/**
 * The controller's own work is the translation either way: what it hands the use
 * cases, and what it gives back. Asserted here rather than only through MockMvc so a
 * field silently dropped from a mapping fails without a database behind it.
 */
class SignupControllerTest {
    private companion object {
        const val APPLICANT_ID = 7L
        const val TOKEN = "sel.ver"
    }

    private val userUseCases = mock<UserUseCases>()
    private val signupUseCases = mock<SignupUseCases>()
    private val controller = SignupController(userUseCases, signupUseCases)

    private fun createRequest() =
        CreateUserRequest(
            username = "applicant",
            initials = "AP",
            firstName = "App",
            lastName = "Licant",
            newsletter = true,
            email = "applicant@example.com",
            discord = "applicant#0001",
            phoneNumber = "0612345678",
            password = "Sup3rSecret!",
        )

    private fun applicant(): User {
        val user =
            User(
                username = "applicant",
                email = "applicant@example.com",
                password = "encoded",
                initials = "AP",
                firstName = "App",
                lastName = "Licant",
                phoneNumber = "0612345678",
                discord = "applicant#0001",
            )
        user.id = APPLICANT_ID
        return user
    }

    @Nested
    inner class SignUp {
        @Test
        fun `registers as an applicant and answers with the session the token belongs to`() {
            val expiry = Instant.now().plusSeconds(7200)
            whenever(userUseCases.create(any(), eq(false))).thenReturn(applicant())
            whenever(signupUseCases.issueSession(APPLICANT_ID)).thenReturn(
                SignupSession(
                    userId = APPLICANT_ID,
                    email = "applicant@example.com",
                    token = TOKEN,
                    expiresAt = expiry,
                ),
            )

            val response = controller.signUp(createRequest())

            assertThat(response.userId).isEqualTo(APPLICANT_ID)
            assertThat(response.email).isEqualTo("applicant@example.com")
            assertThat(response.signupToken).isEqualTo(TOKEN)
            assertThat(response.expiresAt).isEqualTo(expiry)
        }

        // isBoard = false is the whole reason this endpoint is separate from POST /users:
        // it is the flag that makes registration answer to the self-service rules.
        @Test
        fun `registers under the self-service rules`() {
            whenever(userUseCases.create(any(), eq(false))).thenReturn(applicant())
            whenever(signupUseCases.issueSession(APPLICANT_ID)).thenReturn(
                SignupSession(APPLICANT_ID, "applicant@example.com", TOKEN, Instant.now()),
            )

            controller.signUp(createRequest())

            verify(userUseCases).create(any(), eq(false))
        }
    }

    @Nested
    inner class ResumeSignup {
        private fun resume(
            profile: SignupResumeProfile? = null,
            address: SignupResumeAddress? = null,
        ) = SignupResume(
            userId = APPLICANT_ID,
            email = "applicant@example.com",
            username = "applicant",
            initials = "AP",
            firstName = "App",
            prefix = "van",
            lastName = "Licant",
            discord = "applicant#0001",
            discordId = "1144058844004233369",
            phoneNumber = "0612345678",
            newsletter = true,
            photoConsent = true,
            emailConfirmed = false,
            conditionsAccepted = false,
            memberProfile = profile,
            address = address,
        )

        @Test
        fun `hands back every field the form has to put back`() {
            whenever(signupUseCases.resumeSession(TOKEN)).thenReturn(
                resume(
                    profile =
                        SignupResumeProfile(
                            dateOfBirth = LocalDate.of(2000, 1, 2),
                            studentNumber = "s1234567",
                            gender = "female",
                            nationality = "NL",
                            bhv = true,
                            ehbo = false,
                            nameOnRosters = true,
                        ),
                    address =
                        SignupResumeAddress(
                            country = "NL",
                            city = "Enschede",
                            street = "Drienerlolaan",
                            houseNumber = "5",
                            zipCode = "7522NB",
                        ),
                ),
            )

            val response = controller.resumeSignup(TOKEN)

            assertThat(response.userId).isEqualTo(APPLICANT_ID)
            assertThat(response.email).isEqualTo("applicant@example.com")
            assertThat(response.username).isEqualTo("applicant")
            assertThat(response.initials).isEqualTo("AP")
            assertThat(response.firstName).isEqualTo("App")
            assertThat(response.prefix).isEqualTo("van")
            assertThat(response.lastName).isEqualTo("Licant")
            assertThat(response.discord).isEqualTo("applicant#0001")
            assertThat(response.discordId).isEqualTo("1144058844004233369")
            assertThat(response.phoneNumber).isEqualTo("0612345678")
            assertThat(response.newsletter).isTrue()
            assertThat(response.photoConsent).isTrue()
            assertThat(response.emailConfirmed).isFalse()
            assertThat(response.conditionsAccepted).isFalse()
            assertThat(response.memberProfile?.dateOfBirth).isEqualTo(LocalDate.of(2000, 1, 2))
            assertThat(response.memberProfile?.studentNumber).isEqualTo("s1234567")
            assertThat(response.memberProfile?.gender).isEqualTo("female")
            assertThat(response.memberProfile?.nationality).isEqualTo("NL")
            assertThat(response.memberProfile?.bhv).isTrue()
            assertThat(response.memberProfile?.ehbo).isFalse()
            assertThat(response.memberProfile?.nameOnRosters).isTrue()
            assertThat(response.address?.country).isEqualTo("NL")
            assertThat(response.address?.city).isEqualTo("Enschede")
            assertThat(response.address?.street).isEqualTo("Drienerlolaan")
            assertThat(response.address?.houseNumber).isEqualTo("5")
            assertThat(response.address?.zipCode).isEqualTo("7522NB")
        }

        // A tab reloaded at the first step has neither yet, and a null there is what
        // sends the applicant back to the step that fills it in.
        @Test
        fun `leaves the profile and address absent when the signup has not reached them`() {
            whenever(signupUseCases.resumeSession(TOKEN)).thenReturn(resume())

            val response = controller.resumeSignup(TOKEN)

            assertThat(response.memberProfile).isNull()
            assertThat(response.address).isNull()
        }
    }

    @Nested
    inner class SaveAddress {
        @Test
        fun `passes the address on under the token it arrived with`() {
            controller.saveAddress(
                TOKEN,
                SignupAddressRequest(
                    country = "NL",
                    city = "Enschede",
                    street = "Drienerlolaan",
                    houseNumber = "5",
                    zipCode = "7522NB",
                ),
            )

            verify(signupUseCases).saveAddress(
                signupToken = TOKEN,
                country = "NL",
                city = "Enschede",
                street = "Drienerlolaan",
                houseNumber = "5",
                zipCode = "7522NB",
            )
        }
    }

    @Nested
    inner class Apply {
        @Test
        fun `reports both halves of the outcome`() {
            whenever(signupUseCases.submitApplication(TOKEN))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val response = controller.apply(TOKEN, SignupApplicationRequest(conditionsAccepted = true))

            assertThat(response.emailConfirmed).isTrue()
            assertThat(response.membershipStarted).isTrue()
        }

        // An unconfirmed address is the normal case here, so the applicant is told the
        // membership has not started rather than that the request failed.
        @Test
        fun `reports a membership that has not started yet`() {
            whenever(signupUseCases.submitApplication(TOKEN))
                .thenReturn(SignupOutcome(emailConfirmed = false, membershipStarted = false))

            val response = controller.apply(TOKEN, SignupApplicationRequest(conditionsAccepted = true))

            assertThat(response.emailConfirmed).isFalse()
            assertThat(response.membershipStarted).isFalse()
        }
    }

    @Nested
    inner class UpdateDetails {
        private fun request(
            photoConsent: Boolean? = null,
            memberProfile: UpsertMemberProfileRequest? = null,
        ) = SignupDetailsRequest(
            username = "applicant",
            initials = "AP",
            firstName = "App",
            prefix = "van",
            lastName = "Licant",
            discord = "applicant#0001",
            discordId = "1144058844004233369",
            phoneNumber = "0612345678",
            newsletter = true,
            photoConsent = photoConsent,
            memberProfile = memberProfile,
        )

        private fun captured(): SignupDetailsData {
            val captor = argumentCaptor<SignupDetailsData>()
            verify(signupUseCases).updateDetails(eq(TOKEN), captor.capture())
            return captor.firstValue
        }

        @Test
        fun `carries every corrected field through`() {
            controller.updateDetails(
                TOKEN,
                request(
                    photoConsent = true,
                    memberProfile =
                        UpsertMemberProfileRequest(
                            dateOfBirth = LocalDate.of(2000, 1, 2),
                            studentNumber = "s1234567",
                            gender = "female",
                            nationality = "NL",
                            bhv = true,
                            ehbo = false,
                            nameOnRosters = true,
                        ),
                ),
            )

            val data = captured()
            assertThat(data.username).isEqualTo("applicant")
            assertThat(data.initials).isEqualTo("AP")
            assertThat(data.firstName).isEqualTo("App")
            assertThat(data.prefix).isEqualTo("van")
            assertThat(data.lastName).isEqualTo("Licant")
            assertThat(data.discord).isEqualTo("applicant#0001")
            assertThat(data.discordId).isEqualTo("1144058844004233369")
            assertThat(data.phoneNumber).isEqualTo("0612345678")
            assertThat(data.newsletter).isTrue()
            assertThat(data.photoConsent).isTrue()
            assertThat(data.memberProfile?.nationality).isEqualTo("NL")
            assertThat(data.memberProfile?.nameOnRosters).isTrue()
        }

        // An omitted consent is a consent not given, not a consent unknown: the domain
        // only carries a boolean, so the absent case has to land on false here.
        @Test
        fun `reads an omitted photo consent as consent withheld`() {
            controller.updateDetails(TOKEN, request(photoConsent = null))

            assertThat(captured().photoConsent).isFalse()
        }

        @Test
        fun `leaves the profile alone when the step did not send one`() {
            controller.updateDetails(TOKEN, request(memberProfile = null))

            assertThat(captured().memberProfile).isNull()
        }
    }

    @Nested
    inner class CorrectEmail {
        @Test
        fun `passes the corrected address on under the token it arrived with`() {
            controller.correctEmail(TOKEN, SignupEmailRequest(email = "corrected@example.com"))

            verify(signupUseCases).correctEmail(signupToken = TOKEN, email = "corrected@example.com")
        }
    }
}
