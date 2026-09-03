package net.blueshell.api.contribution.domain

import net.blueshell.api.auth.domain.SignupCompletionService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.LocalDate

/**
 * The joining ask against a real database: a completed signup writes the record and queues
 * the email, in one transaction, through the wiring rather than around it.
 *
 * The unit tests cover what the ask decides and what the email says. This covers the part
 * only a running context can answer — that `auth` reaches `contribution` through the port at
 * all, and that the row and the job both come out the other side.
 */
@SpringBootTest
class JoiningContributionAskIT : UserTestSupport() {

    @Autowired
    private lateinit var completion: SignupCompletionService

    @Autowired
    private lateinit var memberProfiles: MemberProfileService

    @Autowired
    private lateinit var reminders: ContributionReminderRepository

    @Autowired
    private lateinit var users: UserService

    private fun period(cutoff: LocalDate) = persist(
        ContributionPeriod(
            startDate = LocalDate.now().minusMonths(1),
            endDate = LocalDate.now().plusMonths(11),
            halfYearCutoffDate = cutoff,
            halfYearFee = 12.50,
            fullYearFee = 20.0,
            alumniFee = 10.0,
        ),
    )

    /** An applicant with everything but a confirmed address. */
    private fun applicant(): User {
        val user = assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST, enabled = false)))
        val profile = memberProfiles.findById(user.id!!)
        profile.conditionsAcceptedAt = Instant.now()
        memberProfiles.update(profile)
        return user
    }

    private fun join(user: User) {
        users.activateUser(user.id!!)
        assertThat(completion.completeIfReady(user.id!!).membershipStarted).isTrue()
    }

    @Test
    fun `a completed signup records the ask and queues the email`() {
        val period = period(cutoff = LocalDate.now().plusMonths(5))
        val user = applicant()

        join(user)

        val asks = reminders.findByContributionPeriod_Id(period.id!!).filter { it.userId == user.id }
        assertThat(asks).hasSize(1)
        val ask = asks.single()
        assertThat(ask.amount).isEqualTo(20.0)
        assertThat(ask.paymentDueDate).isEqualTo(LocalDate.now().plusWeeks(2))

        val jobs = findJobsByType(EmailJobs.JoiningContribution.type)
        assertThat(jobs).hasSize(1)
        assertThat(jobs.single().payload).contains("\"contributionReminderId\":${ask.id}")
    }

    // The rule the treasurer's own tooling uses, reaching the ask through the real wiring.
    @Test
    fun `a membership starting after the cutoff is asked for half a year`() {
        val period = period(cutoff = LocalDate.now().minusDays(1))
        val user = applicant()

        join(user)

        val ask = reminders.findByContributionPeriod_Id(period.id!!).single { it.userId == user.id }
        assertThat(ask.amount).isEqualTo(12.50)
    }

    // Nothing to price against, so the member joins and hears nothing — but still joins.
    @Test
    fun `a signup with no contribution period still becomes a membership`() {
        val user = applicant()

        join(user)

        assertThat(findJobsByType(EmailJobs.JoiningContribution.type)).isEmpty()
    }
}
