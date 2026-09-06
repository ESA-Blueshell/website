package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime
import java.util.Optional

class CohortRemediationServiceTest {

    private val cohorts: CohortRepository = mockk()
    private val subjects: CohortSubjectRepository = mockk()
    private val members: CohortMemberRepository = mockk(relaxed = true)
    private val externalIds: ExternalIdMappingService = mockk()
    private val targetIds: CohortTargetIds = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val port = RecordingTargetStrategy()
    private val service = CohortRemediationService(
        cohortRepo = cohorts,
        subjectRepo = subjects,
        memberRepo = members,
        ledger = CohortLedger(members),
        externalIds = externalIds,
        targetIds = targetIds,
        strategies = TargetStrategies(listOf(port)),
        jobs = jobs,
        transactionManager = ImmediateTransactionManager(),
    )

    init {
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNotNull(any(), any()) } returns null
    }

    @Test
    fun `verifyCohort fetches remote members outside a transaction and applies ledger changes`() {
        val subject = subject(7L)
        val cohort = cohort(99L, subject.id!!)
        val confirmed = member(cohort, subject, userId = 1L)
        val missingWithExternalId = member(
            cohort,
            subject,
            userId = 2L,
            externalUserId = "ext-2",
            syncedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
            verifiedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
        )
        val missingWithoutExternalId = member(cohort, subject, userId = 3L)
        val matchingStranger = member(
            cohort,
            subject,
            userId = null,
            externalUserId = "ext-1",
            verifiedAt = LocalDateTime.parse("2026-01-02T12:00:00"),
            label = "old stranger",
        )
        val staleStranger = member(
            cohort,
            subject,
            userId = null,
            externalUserId = "stale",
            verifiedAt = LocalDateTime.parse("2026-01-03T12:00:00"),
        )
        port.remote = listOf(
            ExternalMember("ext-1", "Alice Remote"),
            ExternalMember("ext-extra", "Extra Remote"),
        )

        every { cohorts.findById(99L) } returns Optional.of(cohort)
        every { subjects.findById(7L) } returns Optional.of(subject)
        every { targetIds.require(any()) } returns "list-99"
        every {
            externalIds.findBatch("USER", setOf(1L, 2L, 3L), TargetSystem.BREVO.name)
        } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-2"),
        )
        every { members.findAllByCohortIdAndUserIdIsNotNull(99L) } returns listOf(
            confirmed,
            missingWithExternalId,
            missingWithoutExternalId,
        )
        every {
            members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(99L, setOf("ext-1"))
        } returns listOf(matchingStranger)
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNull(99L, "ext-extra") } returns null
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNotNull(99L, "ext-extra") } returns null
        every { members.findAllByCohortIdAndUserIdIsNull(99L) } returns listOf(staleStranger)
        every { members.save(any<CohortMember>()) } answers { firstArg() }

        service.verifyCohort(99L)

        assertThat(port.listCalls).isEqualTo(1)
        assertThat(port.lastExternalCohortId).isEqualTo("list-99")
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
            )
            jobs.runAsync(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(3L))
        }
        verify(exactly = 0) {
            jobs.runAsync(CohortJobs.RemoveExternalMember, any<CohortJobs.RemoveExternalMemberPayload>())
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
        val subject = subject(7L)
        val cohort = cohort(99L, subject.id!!)
        val stranger = member(
            cohort,
            subject,
            userId = null,
            externalUserId = "ext-9",
            verifiedAt = LocalDateTime.parse("2026-03-01T08:00:00"),
        )
        every { cohorts.findById(99L) } returns Optional.of(cohort)
        every { targetIds.require(any()) } returns "list-99"
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNull(99L, "ext-9") } returns stranger

        service.removeExternalMember(99L, "ext-9")

        assertThat(port.removeCalls).containsExactly("ext-9" to "list-99")
        verify { members.delete(stranger) }
        verify(exactly = 1) { members.delete(any<CohortMember>()) }
    }

    @Test
    fun `linkUser folds a known stranger into an existing desired row`() {
        val subject = subject(44L)
        val cohort = cohort(55L, subject.id!!)
        val stranger = member(
            cohort,
            subject,
            userId = null,
            externalUserId = "ext-7",
            verifiedAt = LocalDateTime.parse("2026-02-01T09:00:00"),
            label = "Linked Remote",
        )
        val desired = member(cohort, subject, userId = 7L)
        val mapping = ExternalIdMapping("USER", 7L, TargetSystem.BREVO.name, "ext-7")

        every { externalIds.linkUser(7L, TargetSystem.BREVO, "ext-7") } returns mapping
        every { cohorts.findBySubjectIdAndSystem(44L, TargetSystem.BREVO.name) } returns cohort
        every { members.findByCohortIdAndExternalUserIdAndUserIdIsNull(55L, "ext-7") } returns stranger
        every { members.findByCohortIdAndUserId(55L, 7L) } returns desired
        every { members.save(any<CohortMember>()) } answers { firstArg() }

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
    fun `repairMissingAdds requires a bound cohort and re-enqueues unsynced desired rows`() {
        val subject = subject(7L)
        val cohort = cohort(99L, subject.id!!).apply { externalId = "list-99" }
        val unsynced = member(cohort, subject, userId = 1L)
        val drifted = member(
            cohort,
            subject,
            userId = 2L,
            externalUserId = "ext-2",
            syncedAt = null,
            verifiedAt = null,
        )
        val alreadySynced = member(
            cohort,
            subject,
            userId = 3L,
            externalUserId = "ext-3",
            syncedAt = LocalDateTime.parse("2026-01-01T12:00:00"),
        )
        every { cohorts.findById(99L) } returns Optional.of(cohort)
        every { targetIds.require(cohort) } returns "list-99"
        every { members.findAllByCohortIdAndUserIdIsNotNull(99L) } returns listOf(unsynced, drifted, alreadySynced)

        val result = service.repairMissingAdds(99L)

        assertThat(result.enqueuedAdds).isEqualTo(2)
        verify {
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(1L, 99L, SyncCohortMembershipIntent.ADD),
            )
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(2L, 99L, SyncCohortMembershipIntent.ADD),
            )
        }
        verify(exactly = 0) {
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(3L, 99L, SyncCohortMembershipIntent.ADD),
            )
        }
    }

    private fun subject(id: Long): CohortSubject =
        CohortSubject(CohortSubjectType.NEWSLETTER_SUBSCRIBERS, "Members").apply { this.id = id }

    private fun cohort(id: Long, subjectId: Long): Cohort =
        Cohort(
            system = TargetSystem.BREVO.name,
            kind = CohortKind.LIST,
            label = "Members",
            subjectId = subjectId,
        ).apply { this.id = id }

    private fun member(
        cohort: Cohort,
        subject: CohortSubject,
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
        label: String? = null,
    ): CohortMember =
        CohortMember(
            cohort = cohort,
            userId = userId,
            subject = subject,
            externalUserId = externalUserId,
            syncedAt = syncedAt,
            verifiedAt = verifiedAt,
            label = label,
        )

    private class RecordingTargetStrategy : TargetStrategy {
        override val descriptor = TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            systemLabel = "Brevo",
            targetLabel = "Brevo list",
            idLabel = "List id",
            capabilities = setOf(
                TargetCapability.READ_MEMBERS,
                TargetCapability.WRITE_MEMBERS,
                TargetCapability.DELETE,
            ),
        )
        var remote: List<ExternalMember> = emptyList()
        var listCalls = 0
        var lastExternalCohortId: String? = null
        var sawTransactionDuringList = false
        val removeCalls = mutableListOf<Pair<String, String>>()

        override fun create(label: String, folder: String?): ExternalTarget = error("not used")
        override fun add(target: ExternalTarget, externalUserId: String) = Unit
        override fun remove(target: ExternalTarget, externalUserId: String) {
            removeCalls += externalUserId to target.externalId
        }
        override fun delete(target: ExternalTarget) = Unit

        override fun members(target: ExternalTarget): List<ExternalMember> {
            listCalls += 1
            lastExternalCohortId = target.externalId
            sawTransactionDuringList = TransactionSynchronizationManager.isActualTransactionActive()
            return remote
        }
    }

    private class ImmediateTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
