package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

/**
 * Tests email job scheduling for contribution reminders.
 *
 * Verifies that when contribution reminders are sent, email jobs are scheduled
 * with correct payloads (ADR-019, ADR-022).
 */
class ContributionReminderServiceTest : ServiceTestSupport() {

    @Autowired
    private lateinit var contributionReminderService: ContributionReminderService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `schedules contribution reminder email job`() {
        // Given: Contribution reminder entity
        val user = createAndSaveUser()
        val period = createAndSavePeriod()
        val reminder = createContributionReminder(user, period)
        val savedReminder = persist(reminder)

        // When: Sending reminder
        contributionReminderService.sendReminder(savedReminder)

        // Then: Email job is scheduled
        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule contribution reminder email job")
            .hasSize(1)

        // And: Job has correct payload
        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job should contain userId and contributionPeriodId")
            .contains("\"userId\":${user.id}")
            .contains("\"contributionPeriodId\":${period.id}")
    }

    @Test
    fun `schedules multiple reminder jobs`() {
        // Given: Multiple contribution reminders
        val user1 = createAndSaveUser("user1")
        val user2 = createAndSaveUser("user2")
        val user3 = createAndSaveUser("user3")
        val period = createAndSavePeriod()

        val reminders = mutableListOf(
            createContributionReminder(user1, period),
            createContributionReminder(user2, period),
            createContributionReminder(user3, period)
        ).map { persist(it) }.toMutableList()

        // When: Sending reminders
        contributionReminderService.sendReminders(reminders)

        // Then: Email jobs are scheduled for each reminder
        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule one email job per reminder")
            .hasSize(reminders.size)
    }

    private fun createContributionReminder(user: User, period: ContributionPeriod): ContributionReminder {
        return ContributionReminder(
            id = ContributionReminder.Id(
                userId = user.id,
                contributionPeriodId = period.id
            ),
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
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        )
        return persist(period)
    }
}
