package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.SyncCohortMembershipIntent
import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortMembershipSyncServiceTest {

    private val cohorts: CohortRepository = mockk()
    private val members: CohortMemberRepository = mockk(relaxed = true)
    private val brevoPort: CohortPort = mockk(relaxed = true) {
        every { system } returns TargetSystem.BREVO
    }
    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val service = CohortMembershipSyncService(
        cohorts = cohorts,
        members = members,
        registry = CohortPortRegistry(listOf(brevoPort)),
        externalIds = externalIds,
        jobs = jobs,
    )

    init {
        // Default: no desired row to stamp (overridden by the stamping test).
        // save echoes its argument so the relaxed return type does not mis-cast.
        every { members.findByCohortIdAndUserId(any(), any()) } returns null
        every { members.save(any<CohortMember>()) } answers { firstArg() }
    }

    @Test
    fun `ADD calls port when both external ids exist`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns mapping("COHORT", 10L, "BREVO", "42")

        service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoPort.addMember("777", "42") }
    }

    @Test
    fun `ADD lazy-creates the cohort externally on first use and stores its id`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns null
        every { brevoPort.createCohort("Members", null) } returns "99"

        service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoPort.createCohort("Members", null) }
        verify { externalIds.upsert("COHORT", 10L, "BREVO", "99") }
        verify { brevoPort.addMember("777", "99") }
    }

    @Test
    fun `ADD stamps externalUserId and observedAt on the desired row after a successful push`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns mapping("COHORT", 10L, "BREVO", "42")
        val row = mockk<CohortMember>(relaxed = true)
        every { members.findByCohortIdAndUserId(10L, 1L) } returns row

        service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoPort.addMember("777", "42") }
        verify { row.externalUserId = "777" }
        verify { row.observedAt = any() }
        verify { members.save(row) }
    }

    @Test
    fun `ADD without a user external id enqueues SyncContact and throws retryable`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null

        assertThatThrownBy {
            service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(CohortMembershipNotReadyException::class.java)

        verify {
            jobs.enqueue(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(1L))
        }
        verify(exactly = 0) { brevoPort.addMember(any(), any()) }
    }

    @Test
    fun `REMOVE calls port when both external ids exist`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { externalIds.find("COHORT", 10L, "BREVO") } returns mapping("COHORT", 10L, "BREVO", "42")

        service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.REMOVE)

        verify { brevoPort.removeMember("777", "42") }
    }

    @Test
    fun `REMOVE is a no-op when an external id is missing`() {
        givenCohort(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null
        every { externalIds.find("COHORT", 10L, "BREVO") } returns null

        service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.REMOVE)

        verify(exactly = 0) { brevoPort.removeMember(any(), any()) }
        verify(exactly = 0) { brevoPort.addMember(any(), any()) }
    }

    @Test
    fun `unknown cohort id throws NonRetryableJobException`() {
        every { cohorts.findById(10L) } returns Optional.empty()

        assertThatThrownBy {
            service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `unknown system on cohort throws NonRetryableJobException`() {
        givenCohort(id = 10L, system = "MARS_NETWORK", label = "Settlers")

        assertThatThrownBy {
            service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `cohort whose system has no registered port throws NonRetryableJobException`() {
        // Cohort's system is a valid TargetSystem value but no matching CohortPort bean exists
        // (GOOGLE_CALENDAR has none yet).
        givenCohort(id = 10L, system = "GOOGLE_CALENDAR", label = "events")

        assertThatThrownBy {
            service.sync(userId = 1L, cohortId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("No CohortPort")
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
}
