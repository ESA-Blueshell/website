package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.job.SyncListMembershipJob
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.shared.enums.ContactSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for [SyncListMembershipJob].
 *
 * No Spring context — instantiate directly with mocks.
 * Verifies that the job dispatches per-adapter contact and list sync jobs.
 */
class SyncListMembershipJobTest {

    private val objectMapper = ObjectMapper()
    private val contactListService: ContactListService = mock()
    private val periods: ContributionPeriodService = mock()
    private val contributions: ContributionService = mock()
    private val jobs: TrackedJobDispatcher = mock()

    private val contactAdapter: ContactAdapter = mock<ContactAdapter>().also {
        whenever(it.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val listAdapter: ListAdapter = mock<ListAdapter>().also {
        whenever(it.system).thenReturn(ContactSystem.LISTMONK)
    }

    private val job = SyncListMembershipJob(
        objectMapper = objectMapper,
        contactListService = contactListService,
        periods = periods,
        contributions = contributions,
        contactAdapters = listOf(contactAdapter),
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
    fun `dispatches contact and list sync jobs when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        // 2 enqueue calls: 1 contact sync (SyncContactForSystem) + 1 list sync (SyncListMembershipForSystem)
        verify(jobs, times(2)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `dispatches only list sync job when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        // 1 enqueue call: list sync only (no contact sync when removing)
        verify(jobs, times(1)).enqueue(any<JobDefinition<Any>>(), any())
    }

    @Test
    fun `creates membership when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(contactListService).createMembership(listId, userId)
    }

    @Test
    fun `deletes membership when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(contactListService).deleteMembership(listId, userId)
    }

    @Test
    fun `does not create membership when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(contactListService, never()).createMembership(any(), any())
    }
}
