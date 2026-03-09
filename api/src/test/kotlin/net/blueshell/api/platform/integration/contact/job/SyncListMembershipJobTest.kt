package net.blueshell.api.platform.integration.contact.job

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.domain.user.application.contact.ContactSystemAdapter
import net.blueshell.api.platform.integration.contact.application.ContactListService
import net.blueshell.api.platform.integration.contact.application.ContactSyncService
import net.blueshell.api.platform.integration.contact.application.job.SyncListMembershipJob
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for [SyncListMembershipJob].
 *
 * No Spring context — instantiate directly with mocks.
 * Verifies that the job dispatches per-system AddToList / RemoveFromList jobs
 * instead of calling adapters directly.
 */
class SyncListMembershipJobTest {

    private val objectMapper = ObjectMapper()
    private val contactSyncService: ContactSyncService = mock()
    private val contactListService: ContactListService = mock()
    private val periods: ContributionPeriodService = mock()
    private val contributions: ContributionService = mock()
    private val listmonkAdapter: ContactSystemAdapter = mock {
        whenever(mock.system).thenReturn(ContactSystem.LISTMONK)
    }
    private val jobs: TrackedJobDispatcher = mock()

    private val job = SyncListMembershipJob(
        objectMapper = objectMapper,
        contactSyncService = contactSyncService,
        contactListService = contactListService,
        periods = periods,
        contributions = contributions,
        listSyncAdapters = listOf(listmonkAdapter),
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
    fun `dispatches AddToList job per adapter when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(jobs).enqueue(
            eq(ContactJobs.AddToList),
            eq(ContactJobs.AddToListPayload(userId, listId, ContactSystem.LISTMONK))
        )
    }

    @Test
    fun `calls contactSyncService to ensure Contact exists when user has contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(true)
        whenever(contactListService.createMembership(listId, userId)).thenReturn(true)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(contactSyncService).syncContact(userId)
    }

    @Test
    fun `dispatches RemoveFromList job per adapter when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(jobs).enqueue(
            eq(ContactJobs.RemoveFromList),
            eq(ContactJobs.RemoveFromListPayload(userId, listId, ContactSystem.LISTMONK))
        )
    }

    @Test
    fun `does not dispatch AddToList when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(jobs, never()).enqueue(eq(ContactJobs.AddToList), any<ContactJobs.AddToListPayload>())
    }

    @Test
    fun `does not call contactSyncService when user has no contribution`() {
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncListMembershipPayload(userId, periodId)))

        verify(contactSyncService, never()).syncContact(any())
    }
}
