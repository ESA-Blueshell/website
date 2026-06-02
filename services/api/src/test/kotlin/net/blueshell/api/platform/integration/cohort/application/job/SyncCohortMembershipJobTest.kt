package net.blueshell.api.platform.integration.cohort.application.job

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.cohort.adapter.CohortAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.CohortJobs.SyncCohortMembershipIntent
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.util.Optional

class SyncCohortMembershipJobTest {

    private val objectMapper = ObjectMapper()
    private val cohorts: CohortRepository = mockk()
    private val brevoAdapter: CohortAdapter = mockk(relaxed = true) {
        every { system } returns TargetSystem.BREVO
    }
    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val job = SyncCohortMembershipJob(
        objectMapper = objectMapper,
        cohorts = cohorts,
        adapters = listOf(brevoAdapter),
        externalIds = externalIds,
        jobs = jobs,
    )

    @Test
    fun `ADD calls adapter when both external ids exist`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns mapping("COHORT", 10L, "BREVO", "42")

        runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoAdapter.addMember("777", "42") }
    }

    @Test
    fun `ADD lazy-creates the cohort externally on first use and stores its id`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns null
        every { brevoAdapter.createCohort("Members", null) } returns "99"

        runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoAdapter.createCohort("Members", null) }
        verify { externalIds.upsert("COHORT", 10L, "BREVO", "99") }
        verify { brevoAdapter.addMember("777", "99") }
    }

    @Test
    fun `ADD without a user external id enqueues SyncContact and throws retryable`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null

        assertThatThrownBy {
            runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(CohortMembershipNotReadyException::class.java)

        verify {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(1L))
        }
        verify(exactly = 0) { brevoAdapter.addMember(any(), any()) }
    }

    @Test
    fun `REMOVE calls adapter when both external ids exist`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns mapping("COHORT", 10L, "BREVO", "42")

        runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.REMOVE)

        verify { brevoAdapter.removeMember("777", "42") }
    }

    @Test
    fun `REMOVE is a no-op when an external id is missing`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null
        every { externalIds.find("COHORT", 10L, "BREVO") } returns null

        runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.REMOVE)

        verify(exactly = 0) { brevoAdapter.removeMember(any(), any()) }
        verify(exactly = 0) { brevoAdapter.addMember(any(), any()) }
    }

    @Test
    fun `unknown cohort id throws NonRetryableJobException`() {
        every { cohorts.findById(10L) } returns Optional.empty()

        assertThatThrownBy {
            runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `unknown system on cohort throws NonRetryableJobException`() {
        givenCohort(id = 10L, system = "MARS_NETWORK", label = "Settlers")

        assertThatThrownBy {
            runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `cohort whose system has no registered adapter throws NonRetryableJobException`() {
        // Cohort's system is a valid TargetSystem value but no matching CohortAdapter bean exists
        // (GOOGLE_CALENDAR has none yet).
        givenCohort(id = 10L, system = "GOOGLE_CALENDAR", label = "events")

        assertThatThrownBy {
            runJob(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("No CohortAdapter")
    }

    private fun runJob(userId: Long, cohortId: Long, intent: SyncCohortMembershipIntent) {
        val json = objectMapper.writeValueAsString(payload(userId, cohortId, intent))
        job.handle(json, executionId = null)
    }

    private fun givenCohort(id: Long, system: String, label: String) {
        val c = mockk<Cohort>()
        every { c.id } returns id
        every { c.system } returns system
        every { c.label } returns label
        every { c.kind } returns CohortKind.LIST
        every { cohorts.findById(id) } returns Optional.of(c)
    }

    private fun mapping(aggregateType: String, aggregateId: Long, system: String, externalId: String): ExternalIdMapping =
        ExternalIdMapping(aggregateType, aggregateId, system, externalId)

    private fun payload(
        userId: Long,
        cohortId: Long,
        intent: SyncCohortMembershipIntent,
    ): CohortJobs.SyncCohortMembershipPayload =
        CohortJobs.SyncCohortMembershipPayload(userId, cohortId, intent)
}
