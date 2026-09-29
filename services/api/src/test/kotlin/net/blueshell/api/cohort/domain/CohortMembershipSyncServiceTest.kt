package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortMembershipSyncServiceTest {
    private val targets: TargetRepository = mockk()
    private val ledger: CohortLedger = mockk(relaxed = true)
    private val target42 = ExternalTarget(TargetSystem.BREVO, "42", TargetKind.LIST, "42")
    private val brevoTarget: TargetStrategy =
        mockk(relaxed = true) {
            every { system } returns TargetSystem.BREVO
            every { handle("42") } returns target42
        }
    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val targetExternalIds: CohortTargetIds = mockk(relaxed = true)
    private val jobs: JobQueue = mockk(relaxed = true)
    private val service =
        CohortMembershipSyncService(
            targets = targets,
            ledger = ledger,
            strategies = TargetStrategies(listOf(brevoTarget)),
            externalIds = externalIds,
            targetExternalIds = targetExternalIds,
            jobs = jobs,
            // A relaxed manager still runs the TransactionTemplate callbacks; the
            // real no-active-transaction guarantee is asserted in
            // CohortProviderTransactionBoundaryIT against a real transaction manager.
            transactionManager = mockk(relaxed = true),
        )

    init {
        every { ledger.markPushed(any(), any(), any(), any()) } returns true
    }

    @Test
    fun `ADD calls the strategy when both external ids exist`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { targetExternalIds.find(any()) } returns "42"

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
    }

    @Test
    fun `ADD without a cohort target fails terminally and does not enqueue materialization`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { targetExternalIds.find(any()) } returns null

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("cohort 10 has no BREVO target")

        verify(exactly = 0) {
            jobs.runAsync(CohortJobs.MaterializeCohortTarget, any<CohortJobs.MaterializeCohortTargetPayload>(), any())
        }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
        verify(exactly = 0) { brevoTarget.create(any(), any()) }
    }

    @Test
    fun `ADD marks the desired row pushed after a successful external add`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { targetExternalIds.find(any()) } returns "42"

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
        verify { ledger.markPushed(10L, 1L, "777", any()) }
    }

    @Test
    fun `ADD whose desired row is gone by the time it lands still pushes, and stamps nothing`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { targetExternalIds.find(any()) } returns "42"
        every { ledger.markPushed(10L, 1L, "777", any()) } returns false

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
    }

    @Test
    fun `ADD without a user external id enqueues SyncContact and throws retryable`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(CohortMembershipNotReadyException::class.java)

        verify {
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(1L), JobTrigger.ANOTHER_JOB)
        }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
    }

    @Test
    fun `REMOVE calls the strategy when both external ids exist`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        every { targetExternalIds.find(any()) } returns "42"

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE)).isNull()

        verify { brevoTarget.remove(target42, "777") }
    }

    @Test
    fun `REMOVE is a no-op when an external id is missing, and says which`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { externalIds.find("USER", 1L, "BREVO") } returns null
        every { targetExternalIds.find(any()) } returns null

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE))
            .isEqualTo("The user has no BREVO contact, so is on no BREVO list.")
        every { externalIds.find("USER", 1L, "BREVO") } returns mapping("USER", 1L, "BREVO", "777")
        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE))
            .isEqualTo("The cohort has no BREVO list linked.")

        verify(exactly = 0) { brevoTarget.remove(any(), any()) }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
    }

    @Test
    fun `unknown cohort id throws NonRetryableJobException`() {
        every { targets.findById(10L) } returns Optional.empty()

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `unknown system on cohort throws NonRetryableJobException`() {
        givenTarget(id = 10L, system = "MARS_NETWORK", label = "Settlers")

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `cohort whose system has no registered strategy throws NonRetryableJobException`() {
        // Target's system is a valid TargetSystem value but no matching TargetStrategy bean
        // exists (GOOGLE_CALENDAR has none yet).
        givenTarget(id = 10L, system = "GOOGLE_CALENDAR", label = "events")

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("No TargetStrategy")
    }

    private fun givenTarget(
        id: Long,
        system: String,
        label: String,
    ) {
        val c = Entities.target()
        c.id = id
        c.system = system
        c.label = label
        c.kind = TargetKind.LIST
        every { targets.findById(id) } returns Optional.of(c)
    }

    private fun mapping(
        aggregateType: String,
        aggregateId: Long,
        system: String,
        externalId: String,
    ): ExternalIdMapping = ExternalIdMapping(aggregateType, aggregateId, system, externalId)
}
