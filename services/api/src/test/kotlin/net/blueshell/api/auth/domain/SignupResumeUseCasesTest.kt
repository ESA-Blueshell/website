package net.blueshell.api.auth.domain

import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Date
import java.time.Instant
import java.time.LocalDate

/**
 * What a reloaded tab gets back. The account is read in full while it is still
 * managed, so every branch here is about a step the applicant has not reached yet
 * rather than about a field that failed to load.
 */
class SignupResumeUseCasesTest {
    private companion object {
        const val APPLICANT_ID = 7L
        const val TOKEN = "sel.ver"
    }

    private val users = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val signupTokens = mock<SignupTokenService>()
    private val completion = mock<SignupCompletionService>()
    private val activation = mock<UserActivationService>()
    private val jobs = mock<JobQueue>()

    private val useCases = SignupUseCases(signupTokens, users, memberProfiles, completion, activation, jobs)

    private fun applicant(id: Long? = APPLICANT_ID): User {
        val user =
            User(
                username = "applicant",
                email = "applicant@example.com",
                password = "encoded",
                initials = "AP",
                firstName = "App",
                prefix = "van",
                lastName = "Licant",
                phoneNumber = "0612345678",
                discord = "applicant#0001",
                newsletter = true,
            )
        user.id = id
        user.photoConsent = true
        whenever(signupTokens.resolveAccount(TOKEN)).thenReturn(SignupAccount(APPLICANT_ID, user))
        return user
    }

    @Test
    fun `issueSession mints a token for the account just registered`() {
        val user = applicant()
        whenever(users.findById(APPLICANT_ID)).thenReturn(user)

        useCases.issueSession(APPLICANT_ID)

        org.mockito.kotlin
            .verify(signupTokens)
            .issue(user)
    }

    @Test
    fun `returns the signup as the form has to put it back`() {
        val user = applicant()
        user.enabled = true
        user.replaceMemberProfile(
            MemberProfile(
                user = user,
                dateOfBirth = Date.valueOf(LocalDate.of(2000, 1, 2)),
                studentNumber = "s1234567",
                gender = "female",
                nationality = "NL",
                bhv = true,
                ehbo = false,
                conditionsAcceptedAt = Instant.now(),
            ),
        )
        user.nameOnRosters = true
        user.replaceAddress(
            Address(
                user = user,
                country = "NL",
                city = "Enschede",
                street = "Drienerlolaan",
                houseNumber = "5",
                zipCode = "7522NB",
            ),
        )

        val resume = useCases.resumeSession(TOKEN)

        assertThat(resume.userId).isEqualTo(APPLICANT_ID)
        assertThat(resume.email).isEqualTo("applicant@example.com")
        assertThat(resume.username).isEqualTo("applicant")
        assertThat(resume.initials).isEqualTo("AP")
        assertThat(resume.firstName).isEqualTo("App")
        assertThat(resume.prefix).isEqualTo("van")
        assertThat(resume.lastName).isEqualTo("Licant")
        assertThat(resume.discord).isEqualTo("applicant#0001")
        assertThat(resume.phoneNumber).isEqualTo("0612345678")
        assertThat(resume.newsletter).isTrue()
        assertThat(resume.photoConsent).isTrue()
        assertThat(resume.emailConfirmed).isTrue()
        assertThat(resume.conditionsAccepted).isTrue()
        assertThat(resume.memberProfile?.dateOfBirth).isEqualTo(LocalDate.of(2000, 1, 2))
        assertThat(resume.memberProfile?.studentNumber).isEqualTo("s1234567")
        assertThat(resume.memberProfile?.gender).isEqualTo("female")
        assertThat(resume.memberProfile?.nationality).isEqualTo("NL")
        assertThat(resume.memberProfile?.bhv).isTrue()
        assertThat(resume.memberProfile?.ehbo).isFalse()
        assertThat(resume.memberProfile?.nameOnRosters).isTrue()
        assertThat(resume.address?.country).isEqualTo("NL")
        assertThat(resume.address?.city).isEqualTo("Enschede")
        assertThat(resume.address?.street).isEqualTo("Drienerlolaan")
        assertThat(resume.address?.houseNumber).isEqualTo("5")
        assertThat(resume.address?.zipCode).isEqualTo("7522NB")
    }

    @Test
    fun `reports a signup that has not reached the profile or the address`() {
        applicant()

        val resume = useCases.resumeSession(TOKEN)

        assertThat(resume.memberProfile).isNull()
        assertThat(resume.address).isNull()
        assertThat(resume.emailConfirmed).isFalse()
        assertThat(resume.conditionsAccepted).isFalse()
    }

    // The conditions are the gate on the last step, so an applied profile that has not
    // been through it has to read as not accepted rather than as absent.
    @Test
    fun `reports unaccepted conditions on a profile that was filled in but not submitted`() {
        val user = applicant()
        user.replaceMemberProfile(MemberProfile(user = user, bhv = false, ehbo = false))

        val resume = useCases.resumeSession(TOKEN)

        assertThat(resume.conditionsAccepted).isFalse()
        assertThat(resume.memberProfile).isNotNull()
        assertThat(resume.memberProfile?.dateOfBirth).isNull()
    }

    @Test
    fun `refuses an account the resolver handed back without an id`() {
        applicant(id = null)

        assertThatThrownBy { useCases.resumeSession(TOKEN) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("has an id")
    }
}
