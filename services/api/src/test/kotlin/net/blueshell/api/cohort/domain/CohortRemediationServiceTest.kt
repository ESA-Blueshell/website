package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional

class CohortRemediationServiceTest {
    private val targets: TargetRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val members: TargetMemberRepository = mockk(relaxed = true)
    private val externalIds: ExternalIdMappingService = mockk()
    private val targetExternalIds: CohortTargetIds = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val runs: TargetReconcileRunRepository = mockk { every { save(any()) } answers { firstArg() } }
    private val resolutions: DriftResolutionRepository = mockk(relaxed = true)
    private val port = RecordingTargetStrategy()
    private val service =
        CohortRemediationService(
            targetRepo = targets,
            cohortRepo = cohorts,
            memberRepo = members,
            ledger = CohortLedger(members),
            externalIds = externalIds,
            targetExternalIds = targetExternalIds,
            strategies = TargetStrategies(listOf(port)),
            jobs = jobs,
            runs = runs,
            resolutions = resolutions,
            transactionManager = ImmediateTransactionManager(),
        )

    init {
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(any(), any()) } returns null
    }

    @Test
    fun `verifyTarget fetches remote members outside a transaction and applies ledger changes`() {
        val cohort = cohort(7L)
        val target = target(99L, cohort.id!!)
        val confirmed = member(target, cohort, userId = 1L)
        val missingWithExternalId =
            member(
                target,
                cohort,
                userId = 2L,
                externalUserId = "ext-2",
                syncedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
                verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
            )
        val missingWithoutExternalId = member(target, cohort, userId = 3L)
        val matchingStranger =
            member(
                target,
                cohort,
                userId = null,
                externalUserId = "ext-1",
                verifiedAt = LocalDateTime.parse("2026-01-02T12:00:00"),
                label = "old stranger",
            )
        val staleStranger =
            member(
                target,
                cohort,
                userId = null,
                externalUserId = "stale",
                verifiedAt = LocalDateTime.parse("2026-01-03T12:00:00"),
            )
        port.remote =
            listOf(
                ExternalMember("ext-1", "Alice Remote"),
                ExternalMember("ext-extra", "Extra Remote"),
            )

        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-99"
        every {
            externalIds.findBatch("USER", setOf(1L, 2L, 3L), TargetSystem.BREVO.name)
        } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
                ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-2"),
            )
        every { members.findAllByTargetIdAndUserIdIsNotNull(99L) } returns
            listOf(
                confirmed,
                missingWithExternalId,
                missingWithoutExternalId,
            )
        every {
            members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
        } returns listOf(matchingStranger)
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNull(99L, "ext-extra") } returns null
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-extra") } returns null
        every { members.findAllByTargetIdAndUserIdIsNull(99L) } returns listOf(staleStranger)
        every { members.save(any<TargetMember>()) } answers { firstArg() }

        service.verifyTarget(99L, null)

        assertThat(port.listCalls).isEqualTo(1)
        assertThat(port.lastExternalTargetId).isEqualTo("list-99")
        assertThat(port.sawTransactionDuringList).isFalse()
        assertThat(confirmed.externalUserId).isEqualTo("ext-1")
        assertThat(confirmed.syncedAt).isNotNull()
        assertThat(confirmed.verifiedAt).isNotNull()
        assertThat(confirmed.label).isEqualTo("Alice Remote")
        assertThat(missingWithExternalId.syncedAt).isNull()
        assertThat(missingWithExternalId.verifiedAt).isNull()
        verify { members.delete(matchingStranger) }
        verify { members.delete(staleStranger) }
        verify {
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(2L, 99L, SyncCohortMembershipIntent.ADD),
                JobTrigger.ANOTHER_JOB,
            )
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(3L), JobTrigger.ANOTHER_JOB)
        }
        verify(exactly = 0) {
            jobs.runAsync(CohortJobs.RemoveExternalMember, any<CohortJobs.RemoveExternalMemberPayload>(), any())
        }
        verify {
            members.save(
                match {
                    it.userId == null &&
                        it.externalUserId == "ext-extra" &&
                        it.verifiedAt != null &&
                        it.label == "Extra Remote"
                },
            )
        }
    }

    @Test
    fun `removeExternalMember removes from the external target and deletes only the stranger row`() {
        val cohort = cohort(7L)
        val target = target(99L, cohort.id!!)
        val stranger =
            member(
                target,
                cohort,
                userId = null,
                externalUserId = "ext-9",
                verifiedAt = LocalDateTime.parse("2026-03-01T08:00:00"),
            )
        every { targets.findById(99L) } returns Optional.of(target)
        every { targetExternalIds.require(any()) } returns "list-99"
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns stranger

        service.removeExternalMember(99L, "ext-9")

        assertThat(port.removeCalls).containsExactly("ext-9" to "list-99")
        verify { members.delete(stranger) }
        verify(exactly = 1) { members.delete(any<TargetMember>()) }
    }

    @Test
    fun `linkUser folds a known stranger into an existing desired row`() {
        val cohort = cohort(44L)
        val target = target(55L, cohort.id!!)
        val stranger =
            member(
                target,
                cohort,
                userId = null,
                externalUserId = "ext-7",
                verifiedAt = LocalDateTime.parse("2026-02-01T09:00:00"),
                label = "Linked Remote",
            )
        val desired = member(target, cohort, userId = 7L)
        val mapping = ExternalIdMapping("USER", 7L, TargetSystem.BREVO.name, "ext-7")

        every { externalIds.linkUser(7L, TargetSystem.BREVO, "ext-7") } returns mapping
        every { targets.findByCohortIdAndSystem(44L, TargetSystem.BREVO.name) } returns target
        every { members.findByTargetIdAndExternalUserIdAndUserIdIsNull(55L, "ext-7") } returns stranger
        every { members.findByTargetIdAndUserId(55L, 7L) } returns desired
        every { members.save(any<TargetMember>()) } answers { firstArg() }

        val result = service.linkUser(44L, 7L, TargetSystem.BREVO, "ext-7")

        assertThat(result).isSameAs(mapping)
        assertThat(desired.externalUserId).isEqualTo("ext-7")
        assertThat(desired.verifiedAt).isEqualTo(stranger.verifiedAt)
        assertThat(desired.syncedAt).isEqualTo(stranger.verifiedAt)
        assertThat(desired.label).isEqualTo("Linked Remote")
        verify { members.save(desired) }
        verify { members.delete(stranger) }
    }

    @Test
    fun `a job for a target that is gone, or whose cohort is, fails terminally and says which`() {
        val loose = target(5L, 7L).apply { cohortId = null }
        every { targets.findById(4L) } returns Optional.empty()
        every { targets.findById(5L) } returns Optional.of(loose)
        every { targets.findById(6L) } returns Optional.of(target(6L, 70L))
        every { cohorts.findById(70L) } returns Optional.empty()

        val refusals =
            listOf<() -> Unit>(
                { service.verifyTarget(4L, null) },
                { service.removeExternalMember(4L, "ext-1") },
                { service.verifyTarget(5L, null) },
                { service.verifyTarget(6L, null) },
            ).map { assertThrows<NonRetryableJobException>(it).message }

        assertThat(refusals).containsExactly(
            "Target 4 not found",
            "Target 4 not found",
            "Target 5 has no cohort",
            "Target 6 references missing cohort 70",
        )
    }

    @Test
    fun `a target or cohort deleted while its members were being read stops the reconcile`() {
        val cohort = cohort(7L)
        val target = target(99L, 7L)
        every { targetExternalIds.require(any()) } returns "list-99"
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targets.findById(99L) } returnsMany listOf(Optional.of(target), Optional.empty())

        val targetGone = assertThrows<NonRetryableJobException> { service.verifyTarget(99L, null) }

        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returnsMany listOf(Optional.of(cohort), Optional.empty())
        val cohortGone = assertThrows<NonRetryableJobException> { service.verifyTarget(99L, null) }

        assertThat(targetGone.message).isEqualTo("Target 99 not found")
        assertThat(cohortGone.message).isEqualTo("Target 99 references missing cohort 7")
    }

    @Test
    fun `an external id two users both claim confirms neither of them`() {
        val cohort = cohort(7L)
        val target = target(99L, 7L)
        val first = member(target, cohort, userId = 1L)
        val second = member(target, cohort, userId = 2L)
        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-99"
        every { externalIds.findBatch("USER", setOf(1L, 2L), TargetSystem.BREVO.name) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
                ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-1"),
            )
        every { members.findAllByTargetIdAndUserIdIsNotNull(99L) } returns listOf(first, second)
        every { members.findAllByTargetIdAndUserIdIsNull(99L) } returns emptyList()
        every { members.save(any<TargetMember>()) } answers { firstArg() }

        service.verifyTarget(99L, null)

        assertThat(listOf(first.externalUserId, second.externalUserId)).containsOnlyNulls()
        assertThat(listOf(first.verifiedAt, second.verifiedAt)).containsOnlyNulls()
    }

    private fun cohort(id: Long): Cohort = Cohort(CohortType.NEWSLETTER_SUBSCRIBERS, "Members").apply { this.id = id }

    private fun target(
        id: Long,
        cohortId: Long,
    ): Target =
        Target(
            system = TargetSystem.BREVO.name,
            kind = TargetKind.LIST,
            label = "Members",
            cohortId = cohortId,
        ).apply { this.id = id }

    private fun member(
        target: Target,
        cohort: Cohort,
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
        label: String? = null,
    ): TargetMember =
        TargetMember(
            target = target,
            userId = userId,
            cohort = cohort,
            externalUserId = externalUserId,
            syncedAt = syncedAt,
            verifiedAt = verifiedAt,
            label = label,
        )

    // Brevo's shape: ids come from the mapping table, and a missing contact is made by the contact sync.
    private inner class RecordingTargetStrategy : TargetStrategy {
        override val descriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = TargetKind.LIST,
            )
        var remote: List<ExternalMember> = emptyList()
        var listCalls = 0
        var lastExternalTargetId: String? = null
        var sawTransactionDuringList = false
        val removeCalls = mutableListOf<Pair<String, String>>()

        override fun create(
            label: String,
            folder: String?,
        ): ExternalTarget = error("not used")

        override fun add(
            external: ExternalTarget,
            externalUserId: String,
        ) = Unit

        override fun remove(
            external: ExternalTarget,
            externalUserId: String,
        ) {
            removeCalls += externalUserId to external.externalId
        }

        override fun delete(external: ExternalTarget) = Unit

        override fun members(external: ExternalTarget): List<ExternalMember> {
            listCalls += 1
            lastExternalTargetId = external.externalId
            sawTransactionDuringList = TransactionSynchronizationManager.isActualTransactionActive()
            return remote
        }

        override fun rename(
            external: ExternalTarget,
            name: String,
        ): ExternalTarget = error("not used")

        override fun createFolder(name: String): List<String> = error("not used")

        override fun memberIds(userIds: Set<Long>): Map<Long, String> =
            externalIds
                .findBatch("USER", userIds, TargetSystem.BREVO.name)
                .mapNotNull { mapping -> mapping.externalId?.takeIf { it.isNotBlank() }?.let { mapping.aggregateId to it } }
                .toMap()

        override fun ownersOf(externalUserIds: Set<String>): Map<String, Long> = emptyMap()

        var reachable = true
        var makes = true

        override fun available() = reachable

        override val makesMemberIds get() = makes

        override fun makeMemberId(userId: Long) {
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(userId), JobTrigger.ANOTHER_JOB)
        }
    }

    private class ImmediateTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(
            transaction: Any,
            definition: TransactionDefinition,
        ) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    @Test
    fun `every reconcile records a run with its trigger and the drift the ledger holds after it`() {
        val cohort = cohort(7L)
        val target = target(99L, cohort.id!!)
        port.remote = emptyList()
        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-99"
        every { externalIds.findBatch(any(), any(), any()) } returns emptyList()
        every { members.findAllByTargetId(99L) } returns
            listOf(
                member(target, cohort, 1L, "e1", syncedAt = LocalDateTime.now(), verifiedAt = LocalDateTime.now()),
                member(target, cohort, userId = 2L),
                member(target, cohort, userId = 3L, externalUserId = "e3", syncedAt = LocalDateTime.now()),
                member(target, cohort, userId = null, externalUserId = "stranger", verifiedAt = LocalDateTime.now()),
            )
        val saved = slot<TargetReconcileRun>()
        every { runs.save(capture(saved)) } answers { saved.captured }

        service.verifyTarget(99L, JobTrigger.SCHEDULED_RUN)

        assertThat(saved.captured.targetId).isEqualTo(99L)
        assertThat(saved.captured.trigger).isEqualTo(JobTrigger.SCHEDULED_RUN)
        assertThat(listOf(saved.captured.inSync, saved.captured.oursOnly, saved.captured.theirsOnly)).containsExactly(1, 2, 1)
        assertThat(saved.captured.unreachable).isZero()

        // A reconcile queued before runs recorded their trigger names none.
        service.verifyTarget(99L, null)
        assertThat(saved.captured.trigger).isNull()
    }

    @Test
    fun `on a system that makes no ids, somebody with none is unreachable and counted apart from drift`() {
        val cohort = cohort(7L)
        val target = target(99L, cohort.id!!)
        port.remote = emptyList()
        port.makes = false
        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "role-99"
        every { externalIds.findBatch(any(), any(), any()) } returns listOf(ExternalIdMapping("USER", 3L, "BREVO", "e3"))
        val rows = listOf(member(target, cohort, userId = 2L), member(target, cohort, userId = 3L))
        every { members.findAllByTargetIdAndUserIdIsNotNull(99L) } returns rows
        every { members.findAllByTargetId(99L) } returns rows
        val saved = slot<TargetReconcileRun>()
        every { runs.save(capture(saved)) } answers { saved.captured }

        assertThat(service.verifyTarget(99L, JobTrigger.SCHEDULED_RUN)).isNull()

        assertThat(listOf(saved.captured.oursOnly, saved.captured.unreachable)).containsExactly(1, 1)
        verify(exactly = 0) { jobs.runAsync(ContactJobs.SyncContact, any(), any()) }
    }

    @Test
    fun `a system that cannot be reached is neither compared nor written`() {
        val cohort = cohort(7L)
        val target = target(99L, cohort.id!!)
        port.reachable = false
        every { targets.findById(99L) } returns Optional.of(target)
        every { cohorts.findById(7L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "role-99"

        assertThat(service.verifyTarget(99L, JobTrigger.SCHEDULED_RUN)).isEqualTo("Brevo cannot be reached now, so nothing was compared.")
        assertThat(service.removeExternalMember(99L, "ext-9")).isEqualTo("Brevo cannot be reached now.")
        assertThat(port.listCalls).isZero()
        assertThat(port.removeCalls).isEmpty()
        verify(exactly = 0) { runs.save(any()) }
    }

    @Test
    fun `a reconcile of an enforced target removes its theirs-only people and records each removal`() {
        val cohort = cohort(8L)
        val target = target(98L, cohort.id!!).apply { enforced = true }
        port.remote = listOf(ExternalMember("stranger", "old@example.com"))
        every { targets.findById(98L) } returns Optional.of(target)
        every { cohorts.findById(8L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-98"
        every { externalIds.findBatch(any(), any(), any()) } returns emptyList()
        every { members.save(any()) } answers { firstArg() }
        val stranger = member(target, cohort, userId = null, externalUserId = "stranger", verifiedAt = LocalDateTime.now())
        every { members.findAllByTargetIdAndUserIdIsNull(98L) } returns
            listOf(
                stranger.apply {
                    label = "old@example.com"
                    createdAt = Instant.parse("2026-10-01T00:00:00Z")
                },
            )
        val recorded = slot<List<DriftResolution>>()
        every { resolutions.saveAll(capture(recorded)) } answers { firstArg() }

        service.verifyTarget(98L, JobTrigger.SCHEDULED_RUN)

        verify {
            jobs.runAsync(CohortJobs.RemoveExternalMember, CohortJobs.RemoveExternalMemberPayload(98L, "stranger"), JobTrigger.ANOTHER_JOB)
        }
        assertThat(recorded.captured.single().action).isEqualTo(DriftResolutionAction.ENFORCED_REMOVE)
        assertThat(recorded.captured.single().resolvedBy).isNull()
        verify(exactly = 0) { jobs.runAsync(CohortJobs.SyncCohortMembership, any(), any()) }
    }

    @Test
    fun `enforcing leaves a contact some member's account claims, and records a removal still under way once`() {
        val cohort = cohort(6L)
        val target = target(96L, cohort.id!!).apply { enforced = true }
        port.remote = listOf(ExternalMember("shared", null), ExternalMember("pending", "p@example.com"))
        every { targets.findById(96L) } returns Optional.of(target)
        every { cohorts.findById(6L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-96"
        every { members.save(any()) } answers { firstArg() }
        // Two accounts claim one contact, so the reconcile confirms neither and leaves it a stranger.
        every { members.findAllByTargetIdAndUserIdIsNotNull(96L) } returns
            listOf(member(target, cohort, userId = 1L), member(target, cohort, userId = 2L))
        every { externalIds.findBatch("USER", setOf(1L, 2L), TargetSystem.BREVO.name) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "shared"),
                ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "shared"),
            )
        val since = Instant.parse("2026-10-01T00:00:00Z")
        val strangers =
            listOf("shared", "pending").map { id ->
                member(target, cohort, userId = null, externalUserId = id).apply { createdAt = since }
            }
        every { members.findAllByTargetIdAndUserIdIsNull(96L) } returns strangers
        every {
            resolutions.existsByTargetIdAndExternalUserIdAndActionAndResolvedAtAfter(
                96L,
                "pending",
                DriftResolutionAction.ENFORCED_REMOVE,
                since,
            )
        } returns true
        val recorded = slot<List<DriftResolution>>()
        every { resolutions.saveAll(capture(recorded)) } answers { firstArg() }

        service.verifyTarget(96L, null)

        verify(exactly = 0) { jobs.runAsync(CohortJobs.RemoveExternalMember, CohortJobs.RemoveExternalMemberPayload(96L, "shared"), any()) }
        verify {
            jobs.runAsync(CohortJobs.RemoveExternalMember, CohortJobs.RemoveExternalMemberPayload(96L, "pending"), JobTrigger.ANOTHER_JOB)
        }
        assertThat(recorded.captured).isEmpty()
    }

    @Test
    fun `a reconcile of a target not enforced removes nobody`() {
        val cohort = cohort(9L)
        val target = target(97L, cohort.id!!)
        port.remote = listOf(ExternalMember("stranger", null))
        every { targets.findById(97L) } returns Optional.of(target)
        every { cohorts.findById(9L) } returns Optional.of(cohort)
        every { targetExternalIds.require(any()) } returns "list-97"
        every { externalIds.findBatch(any(), any(), any()) } returns emptyList()
        every { members.save(any()) } answers { firstArg() }

        service.verifyTarget(97L, null)

        verify(exactly = 0) { jobs.runAsync(CohortJobs.RemoveExternalMember, any(), any()) }
        verify(exactly = 0) { resolutions.saveAll(any<List<DriftResolution>>()) }
    }
}
