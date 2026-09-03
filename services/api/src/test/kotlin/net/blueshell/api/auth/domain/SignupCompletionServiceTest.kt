package net.blueshell.api.auth.domain

import net.blueshell.api.contribution.api.JoiningContributionAsk
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

// One test per precondition, each proving that removing it would let a membership
// through. Together they are the invariant.
class SignupCompletionServiceTest {

    private val users = mock<UserService>()
    private val memberships = mock<MembershipService>()
    private val signupTokens = mock<SignupTokenService>()
    private val joiningAsk = mock<JoiningContributionAsk>()
    private val service = SignupCompletionService(users, memberships, signupTokens, joiningAsk)

    private fun applicant(
        enabled: Boolean = true,
        hasProfile: Boolean = true,
        conditionsAcceptedAt: Instant? = Instant.now(),
        hasAddress: Boolean = true,
        alreadyMember: Boolean = false,
    ): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(USER_ID)
        whenever(user.enabled).thenReturn(enabled)
        whenever(user.addressId).thenReturn(if (hasAddress) 99L else null)
        if (hasProfile) {
            val profile = mock<MemberProfile>()
            whenever(profile.conditionsAcceptedAt).thenReturn(conditionsAcceptedAt)
            whenever(user.memberProfile).thenReturn(profile)
        } else {
            whenever(user.memberProfile).thenReturn(null)
        }
        whenever(users.findById(USER_ID)).thenReturn(user)
        whenever(memberships.existsActiveMembershipByUserId(USER_ID)).thenReturn(alreadyMember)
        return user
    }

    @Test
    fun `creates the membership when every fact is present`() {
        val user = applicant()
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] }

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome).isEqualTo(SignupOutcome(emailConfirmed = true, membershipStarted = true))
        val created = argThatWasCreated()
        assertThat(created.user).isSameAs(user)
        assertThat(created.startDate).isEqualTo(LocalDate.now())
    }

    @Test
    fun `retires the signup token once the membership starts`() {
        applicant()
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] }

        service.completeIfReady(USER_ID)

        verify(signupTokens).retire(USER_ID)
    }

    @Test
    fun `refuses an unconfirmed email address`() {
        applicant(enabled = false)

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome).isEqualTo(SignupOutcome(emailConfirmed = false, membershipStarted = false))
        assertNothingCommitted()
    }

    @Test
    fun `refuses a missing member profile`() {
        applicant(hasProfile = false)

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome).isEqualTo(SignupOutcome(emailConfirmed = true, membershipStarted = false))
        assertNothingCommitted()
    }

    @Test
    fun `refuses an unaccepted set of conditions`() {
        applicant(conditionsAcceptedAt = null)

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome).isEqualTo(SignupOutcome(emailConfirmed = true, membershipStarted = false))
        assertNothingCommitted()
    }

    @Test
    fun `refuses a missing address`() {
        applicant(hasAddress = false)

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome).isEqualTo(SignupOutcome(emailConfirmed = true, membershipStarted = false))
        assertNothingCommitted()
    }

    @Test
    fun `is inert for somebody who is already a member`() {
        applicant(alreadyMember = true)

        val outcome = service.completeIfReady(USER_ID)

        assertThat(outcome.membershipStarted).isFalse()
        assertNothingCommitted()
    }

    @Test
    fun `a second call does not create a second membership`() {
        applicant()
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] }
        service.completeIfReady(USER_ID)

        // What the caller sees on the way back in: the membership now exists.
        whenever(memberships.existsActiveMembershipByUserId(USER_ID)).thenReturn(true)
        val second = service.completeIfReady(USER_ID)

        assertThat(second.membershipStarted).isFalse()
        verify(memberships, org.mockito.kotlin.times(1)).create(any())
    }

    @Test
    fun `asks the new member for their contribution, for the day the membership starts`() {
        applicant()
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] }

        service.completeIfReady(USER_ID)

        verify(joiningAsk).askOnJoining(USER_ID, LocalDate.now())
    }

    @Test
    fun `a second call does not ask twice`() {
        applicant()
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] }
        service.completeIfReady(USER_ID)

        whenever(memberships.existsActiveMembershipByUserId(USER_ID)).thenReturn(true)
        service.completeIfReady(USER_ID)

        verify(joiningAsk, org.mockito.kotlin.times(1)).askOnJoining(any(), any())
    }

    @Test
    fun `reports a confirmed address even when it cannot commit`() {
        applicant(hasAddress = false)

        assertThat(service.completeIfReady(USER_ID).emailConfirmed).isTrue()
    }

    private fun argThatWasCreated(): Membership {
        val captor = org.mockito.kotlin.argumentCaptor<Membership>()
        verify(memberships).create(captor.capture())
        return captor.firstValue
    }

    private fun assertNothingCommitted() {
        verify(memberships, never()).create(any())
        verify(signupTokens, never()).retire(any())
        // Nobody who is not a member is asked to pay for being one.
        verify(joiningAsk, never()).askOnJoining(any(), any())
    }

    private companion object {
        const val USER_ID = 7L
    }
}
