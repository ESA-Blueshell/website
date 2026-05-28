package net.blueshell.api.platform.integration.contact.application.job

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.integration.contact.application.ContributionPeriodListResolver
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

class SyncAllPeriodListsJobTest {

    private val listResolver: ContributionPeriodListResolver = mock()
    private val periods: ContributionPeriodService = mock()
    private val contributions: ContributionService = mock()
    private val jobs: TrackedJobDispatcher = mock()
    private val objectMapper: ObjectMapper = JsonMapper.builder().build()

    private val job = SyncAllPeriodListsJob(
        objectMapper = objectMapper,
        listResolver = listResolver,
        periods = periods,
        contributions = contributions,
        jobs = jobs,
    )

    @Test
    fun `links a contact list to each period and enqueues a ProcessListMembership per contribution`() {
        val periodA = period(id = 1L, hasList = false, startYear = 2024, endYear = 2025)
        val periodB = period(id = 2L, hasList = true,  startYear = 2025, endYear = 2026, listId = 99L)
        whenever(periods.findAll()).thenReturn(mutableListOf(periodA, periodB))

        val createdList = mock<ContactList>().also { whenever(it.id).thenReturn(42L) }
        val existingList = mock<ContactList>().also { whenever(it.id).thenReturn(99L) }
        whenever(listResolver.resolve(periodA)).thenReturn(createdList)
        whenever(listResolver.resolve(periodB)).thenReturn(existingList)

        val cA1 = contribution(101L)
        val cA2 = contribution(102L)
        val cB1 = contribution(101L)
        whenever(contributions.findByContributionPeriodId(1L)).thenReturn(mutableListOf(cA1, cA2))
        whenever(contributions.findByContributionPeriodId(2L)).thenReturn(mutableListOf(cB1))

        invokeJob()

        verify(listResolver).resolve(periodA)
        verify(listResolver).resolve(periodB)

        verify(jobs).enqueue(ContactJobs.ProcessListMembership, ContactJobs.ProcessListMembershipPayload(101L, 1L))
        verify(jobs).enqueue(ContactJobs.ProcessListMembership, ContactJobs.ProcessListMembershipPayload(102L, 1L))
        verify(jobs).enqueue(ContactJobs.ProcessListMembership, ContactJobs.ProcessListMembershipPayload(101L, 2L))
    }

    @Test
    fun `one period failing does not stop the rest, but the job throws to mark partial failure`() {
        val periodA = period(id = 1L, hasList = true, listId = 10L, startYear = 2024, endYear = 2025)
        val periodB = period(id = 2L, hasList = true, listId = 20L, startYear = 2025, endYear = 2026)
        whenever(periods.findAll()).thenReturn(mutableListOf(periodA, periodB))
        val c7 = contribution(7L)
        whenever(contributions.findByContributionPeriodId(1L)).thenThrow(RuntimeException("boom"))
        whenever(contributions.findByContributionPeriodId(2L)).thenReturn(mutableListOf(c7))

        org.junit.jupiter.api.assertThrows<IllegalStateException> { invokeJob() }

        verify(jobs).enqueue(ContactJobs.ProcessListMembership, ContactJobs.ProcessListMembershipPayload(7L, 2L))
    }

    private fun period(id: Long, hasList: Boolean, startYear: Int, endYear: Int, listId: Long? = null): ContributionPeriod =
        ContributionPeriod(
            startDate = LocalDate.of(startYear, 1, 1),
            endDate = LocalDate.of(endYear, 1, 1),
            contactListId = if (hasList) listId else null,
        ).apply { this.id = id }

    private fun contribution(userId: Long): Contribution {
        val c = mock<Contribution>()
        whenever(c.userId).thenReturn(userId)
        return c
    }

    /** handlePayload is protected, so drive the job through the public handle. */
    private fun invokeJob() {
        job.handle(objectMapper.writeValueAsString(ContactJobs.SyncAllPeriodListsPayload()), null)
    }
}
