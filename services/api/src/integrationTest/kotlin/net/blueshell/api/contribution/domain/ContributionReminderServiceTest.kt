package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

/**
 * Recording a contribution reminder: the row is written and its email queued together, and
 * the job names the row it was written for (ADR-019, ADR-022).
 */
class ContributionReminderServiceTest : ServiceTestSupport() {

    @Autowired
    private lateinit var contributionReminderService: ContributionReminderService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `records the ask and schedules its email job`() {
        // Given: Contribution reminder entity
        val user = createAndSaveUser()
        val period = createAndSavePeriod()

        // When: Recording the ask
        val savedReminder = contributionReminderService.record(createContributionReminder(user, period))

        // Then: the row is written and its email job scheduled
        assertThat(savedReminder.id)
            .describedAs("The row is written before its email is queued")
            .isNotNull()

        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule contribution reminder email job")
            .hasSize(1)

        // And: the job names the ask it was written for, not the pair — a member can be
        // asked for the same period more than once, so the pair no longer names one row.
        assertThat(jobs.first().payload)
            .describedAs("Job should name the reminder it sends")
            .contains("\"contributionReminderId\":${savedReminder.id}")
    }

    @Test
    fun `records each ask in a batch and schedules one job apiece`() {
        // Given: Multiple contribution reminders
        val user1 = createAndSaveUser("user1")
        val user2 = createAndSaveUser("user2")
        val user3 = createAndSaveUser("user3")
        val period = createAndSavePeriod()

        // When: Recording the asks
        val reminders = contributionReminderService.recordAll(
            listOf(
                createContributionReminder(user1, period),
                createContributionReminder(user2, period),
                createContributionReminder(user3, period),
            ),
        )

        // Then: Email jobs are scheduled for each reminder
        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule one email job per reminder")
            .hasSize(reminders.size)
    }

    private fun createContributionReminder(user: User, period: ContributionPeriod): ContributionReminder {
        return ContributionReminder(
            user = user,
            contributionPeriod = period,
        )
    }

    private fun createAndSaveUser(username: String = "testuser"): User {
        val user = User(
            username = username,
            email = "$username@example.com",
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }

    private fun createAndSavePeriod(): ContributionPeriod {
        val period = ContributionPeriod(
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            halfYearCutoffDate = LocalDate.of(2024, 7, 1),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        )
        return persist(period)
    }
}
