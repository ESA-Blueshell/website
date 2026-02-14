package net.blueshell.api.domain.contribution.application

import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests email job scheduling for contribution reminders.
 *
 * Verifies that when contribution reminders are sent, email jobs are scheduled
 * with correct payloads (ADR-019, ADR-022).
 */
class ContributionReminderServiceTest : ServiceTestSupport() {

    @Autowired
    private lateinit var contributionReminderService: ContributionReminderService

    @Test
    fun `schedules contribution reminder email job`() {
        // Given: Contribution reminder entity
        val reminder = createContributionReminder(userId = 1L, contributionPeriodId = 100L)
        val savedReminder = persist(reminder)

        // When: Sending reminder
        contributionReminderService.sendReminder(savedReminder)

        // Then: Email job is scheduled
        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule contribution reminder email job")
            .isNotEmpty

        // And: Job has correct payload
        val jobPayload = jobs.first().payload
        assertThat(jobPayload)
            .describedAs("Job should contain userId")
            .contains("\"userId\":1")
            .contains("\"contributionPeriodId\":100")
    }

    @Test
    fun `schedules multiple reminder jobs`() {
        // Given: Multiple contribution reminders
        val reminders = mutableListOf(
            createContributionReminder(userId = 1L, contributionPeriodId = 100L),
            createContributionReminder(userId = 2L, contributionPeriodId = 100L),
            createContributionReminder(userId = 3L, contributionPeriodId = 100L)
        ).map { persist(it) }.toMutableList()

        // When: Sending reminders
        contributionReminderService.sendReminders(reminders)

        // Then: Email jobs are scheduled for each reminder
        val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
        assertThat(jobs)
            .describedAs("Should schedule one email job per reminder")
            .hasSize(reminders.size)
    }

    private fun createContributionReminder(userId: Long, contributionPeriodId: Long): net.blueshell.api.domain.contribution.persistence.ContributionReminder {
        val reminder = net.blueshell.api.domain.contribution.persistence.ContributionReminder()
        reminder.id = net.blueshell.api.domain.contribution.persistence.ContributionReminder.Id(
            userId = userId,
            contributionPeriodId = contributionPeriodId
        )
        return reminder
    }
}
