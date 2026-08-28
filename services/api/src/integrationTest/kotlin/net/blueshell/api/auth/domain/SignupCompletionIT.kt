package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

// The invariant of ADR-025, against a real database: whichever of the two facts
// lands second is the one that starts the membership, and neither alone does.
@SpringBootTest
class SignupCompletionIT : UserTestSupport() {

    @Autowired
    private lateinit var completion: SignupCompletionService

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    @Autowired
    private lateinit var memberships: MemberRepository

    @Autowired
    private lateinit var users: net.blueshell.api.user.api.UserService

    private fun applicant(enabled: Boolean) =
        assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST, enabled = enabled)))

    private fun acceptConditions(userId: Long) {
        val profile = memberProfiles.findById(userId)
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)
    }

    @Test
    fun `application submitted first, then the email confirmed`() {
        val user = applicant(enabled = false)
        acceptConditions(user.id!!)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted)
            .describedAs("an unconfirmed address must not start a membership")
            .isFalse()

        users.activateUser(user.id!!)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isTrue()
        assertThat(memberships.findByUser_Id(user.id!!)).hasSize(1)
        assertThat(refreshUser(user).hasAuthority(Role.MEMBER)).isTrue()
    }

    @Test
    fun `email confirmed first, then the application submitted`() {
        val user = applicant(enabled = true)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted)
            .describedAs("a confirmed address alone must not start a membership")
            .isFalse()

        acceptConditions(user.id!!)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isTrue()
        assertThat(memberships.findByUser_Id(user.id!!)).hasSize(1)
    }

    @Test
    fun `calling twice does not start a second membership`() {
        val user = applicant(enabled = true)
        acceptConditions(user.id!!)

        completion.completeIfReady(user.id!!)
        val second = completion.completeIfReady(user.id!!)

        assertThat(second.membershipStarted).isFalse()
        assertThat(memberships.findByUser_Id(user.id!!)).hasSize(1)
    }

    @Test
    fun `an address on file is not enough without the conditions accepted`() {
        val user = applicant(enabled = true)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isFalse()
        assertThat(memberships.findByUser_Id(user.id!!)).isEmpty()
    }

    @Test
    fun `an accepted set of conditions is not enough without an address`() {
        val user = assignMemberProfile(createUserWithRole(Role.GUEST, enabled = true))
        acceptConditions(user.id!!)

        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isFalse()
        assertThat(memberships.findByUser_Id(user.id!!)).isEmpty()
    }

    @Test
    fun `an account with no member profile never becomes a member`() {
        val user = assignAddress(createUserWithRole(Role.GUEST, enabled = true))

        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isFalse()
        assertThat(memberships.findByUser_Id(user.id!!)).isEmpty()
    }
}
