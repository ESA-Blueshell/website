package net.blueshell.api.platform.integration.queue

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.mock.MockCalendarAdapter
import net.blueshell.api.platform.integration.mock.MockContactAdapter
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.CalendarEventRef
import net.blueshell.api.shared.job.CalendarJobs
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Integration tests verifying that jobs are actually executed after being
 * scheduled via auto-dispatch.
 *
 * In production, jobs are enqueued inside `@Transactional` service methods or
 * event listeners. The dispatcher defers [JobExecutor.executeAsync] until after
 * the enclosing transaction commits, so the async thread always sees the
 * committed [JobExecution] row.
 *
 * Each test reproduces this by enqueuing a job inside a transaction — exactly
 * matching the production call path. The deferred dispatch fires after the
 * transaction commits, and the test asserts [JobExecutionStatus.SUCCESS].
 */
@TestPropertySource(properties = ["app.jobs.auto-dispatch=true"])
class JobAutoDispatchIT : UserTestSupport() {

    @Autowired
    private lateinit var jobs: TrackedJobDispatcher

    @Autowired
    private lateinit var mockCalendarAdapter: MockCalendarAdapter

    @Autowired
    private lateinit var mockContactAdapter: MockContactAdapter

    @BeforeEach
    fun clearMocks() {
        emailTransportClient.reset()
        mockCalendarAdapter.clear()
        mockContactAdapter.clear()
    }

    // ── Email: Recovery (password reset) ─────────────────────────────────

    @Test
    fun `recovery email job executes after auto-dispatch`() {
        val user = createUserWithRole(Role.MEMBER)

        enqueueInTransaction {
            jobs.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(user.id!!, "reset-token", ResetType.PASSWORD_RESET)
            )
        }

        awaitJobStatus(EmailJobs.Recovery.type, JobExecutionStatus.SUCCESS)
    }

    // ── Email: Event signup ──────────────────────────────────────────────

    @Test
    fun `event signup email job executes after auto-dispatch`() {
        val event = createEventFixture()
        val guestAccessToken = "guest-token-${System.currentTimeMillis()}"
        val guest = createGuestFixture(accessToken = guestAccessToken)
        val signUp = createEventSignUpFixture(event, user = null, guest = guest)

        enqueueInTransaction {
            jobs.enqueue(
                EmailJobs.EventSignup,
                EmailJobs.EventSignupPayload(signUp.id!!, guestAccessToken)
            )
        }

        awaitJobStatus(EmailJobs.EventSignup.type, JobExecutionStatus.SUCCESS)
    }

    // ── Email: Contribution reminder ─────────────────────────────────────

    @Test
    fun `contribution reminder email job executes after auto-dispatch`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createReminder(user, period)

        enqueueInTransaction {
            jobs.enqueue(
                EmailJobs.ContributionReminder,
                EmailJobs.ContributionReminderPayload(user.id!!, period.id!!)
            )
        }

        awaitJobStatus(EmailJobs.ContributionReminder.type, JobExecutionStatus.SUCCESS)
    }

    // ── Calendar: Sync event ─────────────────────────────────────────────

    @Test
    fun `calendar sync event job executes after auto-dispatch`() {
        val event = createEventFixture(approved = true)

        enqueueInTransaction {
            jobs.enqueue(
                CalendarJobs.SyncEvent,
                CalendarEventRef(event.id!!)
            )
        }

        awaitJobStatus(CalendarJobs.SyncEvent.type, JobExecutionStatus.SUCCESS)
    }

    // ── Contact: Sync contact ────────────────────────────────────────────

    @Test
    fun `sync contact job executes after auto-dispatch`() {
        val user = createUserWithRole(Role.MEMBER)

        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncContact,
                ContactJobs.SyncContactPayload(user.id!!)
            )
        }

        awaitJobStatus(ContactJobs.SyncContact.type, JobExecutionStatus.SUCCESS)
    }

    // ── Contact: Delete contact ──────────────────────────────────────────

    @Test
    fun `delete contact job executes after auto-dispatch`() {
        val user = createUserWithRole(Role.MEMBER)

        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.DeleteContact,
                ContactJobs.DeleteContactPayload(user.id!!)
            )
        }

        awaitJobStatus(ContactJobs.DeleteContact.type, JobExecutionStatus.SUCCESS)
    }

    // ── Contact: Sync list membership ────────────────────────────────────

    @Test
    fun `sync list membership job executes after auto-dispatch`() {
        val user = createUserWithRole(Role.MEMBER)
        val period = createContributionPeriodFixture()
        createContribution(user, period)

        enqueueInTransaction {
            jobs.enqueue(
                ContactJobs.SyncListMembership,
                ContactJobs.SyncListMembershipPayload(user.id!!, period.id!!)
            )
        }

        awaitJobStatus(ContactJobs.SyncListMembership.type, JobExecutionStatus.SUCCESS)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Enqueues a job inside a transaction, matching the production call path.
     * With auto-dispatch enabled, [JobDispatcher] defers [JobExecutor.executeAsync]
     * until after the transaction commits via [TransactionSynchronization.afterCommit].
     */
    private fun enqueueInTransaction(enqueue: () -> Unit) {
        transactionTemplate.executeWithoutResult {
            enqueue()
        }
    }

    /**
     * Polls until the first job of the given type reaches the expected status,
     * or fails after timeout.
     */
    private fun awaitJobStatus(
        jobType: String,
        expected: JobExecutionStatus,
        timeoutMs: Long = 5_000,
        pollMs: Long = 100
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val executions = findJobsByType(jobType)
            if (executions.isNotEmpty()) {
                val execution = jobExecutions.findById(executions.first().id!!).orElseThrow()
                if (execution.status == expected) return
            }
            Thread.sleep(pollMs)
        }

        val executions = findJobsByType(jobType)
        assertThat(executions)
            .describedAs("Expected at least one job execution for type $jobType")
            .isNotEmpty

        val actual = jobExecutions.findById(executions.first().id!!).orElseThrow()
        assertThat(actual.status)
            .describedAs(
                "Job $jobType should have reached $expected after auto-dispatch, " +
                    "but was ${actual.status}. This indicates the async executor could not " +
                    "find the job execution row before the transaction committed."
            )
            .isEqualTo(expected)
    }

    private fun createReminder(user: User, period: ContributionPeriod) {
        val reminder = ContributionReminder(
            id = ContributionReminder.Id(user.id, period.id),
            user = user,
            contributionPeriod = period,
        )
        persist(reminder)
    }

    private fun createContribution(user: User, period: ContributionPeriod) {
        val contribution = Contribution(
            id = Contribution.Id(user.id, period.id),
            user = user,
            contributionPeriod = period
        )
        persist(contribution)
    }
}
