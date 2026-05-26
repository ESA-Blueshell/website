package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.adapter.ContactListAdapter
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.job.ProcessListMembershipJob
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for [ProcessListMembershipJob].
 *
 * No Spring context — instantiate directly with mocks.
 * Verifies that the job enqueues per-adapter list sync jobs plus, when
 * the user has a contribution, a queued SyncContact job.
 */
class ProcessListMembershipJobTest {

    private val objectMapper = ObjectMapper()
    private val contactListService: ContactListService = mock()
    private val periods: ContributionPeriodService = mock()
    private val contributions: ContributionService = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private val listAdapter: ContactListAdapter = mock<ContactListAdapter>().also {
        whenever(it.system).thenReturn(ContactSystem.BREVO)
    }

    private val job = ProcessListMembershipJob(
        objectMapper = objectMapper,
        contactListService = contactListService,
        periods = periods,
        contributions = contributions,
        listAdapters = listOf(listAdapter),
        jobs = jobs,
    )

    private val userId = 1L
    private val periodId = 2L
    private val listId = 10L

    @BeforeEach
    fun setUp() {
        val period = ContributionPeriod(
            startDate = LocalDate.of(2024, 9, 1),
            endDate = LocalDate.of(2025, 8, 31)
        ).apply {
            id = periodId
            contactListId = listId
        }
        val contactList = ContactList(name = "Contribution Paid 2024 - 2025").apply { id = listId }

        whenever(periods.findById(periodId)).thenReturn(period)
        whenever(contactListService.findById(listId)).thenReturn(contactList)
    }

    @Test
    fun `enqueues SyncContact and one list sync job when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.ProcessListMembershipPayload(userId, periodId)))

        verify(jobs).enqueue(eq(ContactJobs.SyncContact), eq(ContactJobs.SyncContactPayload(userId)))
        // SyncContact + one SyncListMembershipToSystem = two enqueue calls
        verify(jobs, times(2)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `dispatches only list sync job and does not enqueue SyncContact when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.ProcessListMembershipPayload(userId, periodId)))

        verify(jobs, never()).enqueue(eq(ContactJobs.SyncContact), any())
        verify(jobs, times(1)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `creates membership when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.ProcessListMembershipPayload(userId, periodId)))

        verify(contactListService).createMembership(listId, userId)
    }

    @Test
    fun `deletes membership when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.ProcessListMembershipPayload(userId, periodId)))

        verify(contactListService).deleteMembership(listId, userId)
    }

    @Test
    fun `does not create membership when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.ProcessListMembershipPayload(userId, periodId)))

        verify(contactListService, never()).createMembership(any(), any())
    }
}
