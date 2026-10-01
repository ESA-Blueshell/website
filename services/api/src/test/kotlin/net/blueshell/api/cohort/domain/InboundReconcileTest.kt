package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class InboundReconcileTest {
    private val cohorts: CohortRepository = mockk()
    private val targets: TargetRepository = mockk()
    private val members: TargetMemberRepository = mockk()
    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val users: UserService = mockk()
    private val writers: MembershipWriters = mockk()
    private val contributionWriter: MembershipWriter = mockk()
    private val definitions: CohortDefinitionRegistry = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val resolutions: DriftResolutions = mockk(relaxed = true)
    private val strategy = RecordingTargetStrategy()
    private val service =
        InboundReconcile(
            cohorts = cohorts,
            targets = targets,
            members = members,
            externalIds = externalIds,
            users = users,
            writers = writers,
            definitions = definitions,
            jobs = jobs,
            strategies = TargetStrategies(listOf(strategy)),
            resolutions = resolutions,
            transactionManager = ImmediateTransactionManager(),
        )

    @Test
    fun `preview is write-free external-id only and classifies duplicate conflict inactive and unmatched`() {
        val given = givenContributionTarget()
        val desired = TargetMember(given.target, userId = 9L, cohort = given.cohort)
        strategy.remote =
            listOf(
                ExternalMember("ext-internal", "Already internal"),
                ExternalMember("ext-1", "Mapped One"),
                ExternalMember("ext-dup", "Duplicate A"),
                ExternalMember("ext-dup", "Duplicate B"),
                ExternalMember("ext-conflict", "Conflict"),
                ExternalMember("ext-missing", "Missing"),
                ExternalMember("ext-inactive", "Inactive"),
            )
        every { members.findAllByTargetIdAndUserIdIsNotNull(20L) } returns listOf(desired)
        every { externalIds.findBatch("USER", setOf(9L), TargetSystem.BREVO.name) } returns
            listOf(
                ExternalIdMapping("USER", 9L, TargetSystem.BREVO.name, "ext-internal"),
            )
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
                ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-conflict"),
                ExternalIdMapping("USER", 3L, TargetSystem.BREVO.name, "ext-conflict"),
                ExternalIdMapping("USER", 4L, TargetSystem.BREVO.name, "ext-inactive"),
            )
        every { users.findAllByIds(setOf(1L, 2L, 3L, 4L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.preview(1L, any()) } returns MembershipPreview(alreadyMember = false)

        val preview = service.preview(10L, 20L)

        assertThat(strategy.listCalls).isEqualTo(1)
        assertThat(strategy.sawTransactionDuringMembers).isFalse()
        assertThat(preview.writerSupported).isTrue()
        assertThat(preview.matched).extracting<Long?> { it.userId }.containsExactly(1L)
        assertThat(preview.matched.single().writable).isTrue()
        assertThat(preview.skipped)
            .extracting<InboundReconcileSkipReason> { it.reason }
            .containsExactlyInAnyOrder(
                InboundReconcileSkipReason.DUPLICATE_REMOTE_ID,
                InboundReconcileSkipReason.MAPPING_CONFLICT,
                InboundReconcileSkipReason.UNMATCHED,
                InboundReconcileSkipReason.MAPPED_USER_INACTIVE,
            )
        assertThat(preview.matched.map { it.externalUserId }).doesNotContain("ext-internal")
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
        verify(exactly = 0) { contributionWriter.apply(any(), any()) }
        verify(exactly = 0) { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>(), any()) }
    }

    @Test
    fun `preview returns disabled writable rows when a cohort cannot be written into`() {
        givenNewsletterTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"))
        every { members.findAllByTargetIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            )
        every { users.findAllByIds(setOf(1L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortType.NEWSLETTER_SUBSCRIBERS) } returns null

        val preview = service.preview(10L, 20L)

        assertThat(preview.writerSupported).isFalse()
        assertThat(preview.matched.single().writable).isFalse()
        assertThat(preview.matched.single().alreadyMember).isFalse()
    }

    @Test
    fun `apply rejects stale preview token before enqueueing`() {
        givenContributionTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"))
        every { members.findAllByTargetIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            )
        every { users.findAllByIds(setOf(1L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.preview(1L, any()) } returns MembershipPreview(alreadyMember = false)

        val preview = service.preview(10L, 20L)
        strategy.remote = listOf(ExternalMember("ext-other", "Changed"))
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns emptyList()
        every { users.findAllByIds(emptySet()) } returns emptyList()

        assertThatThrownBy {
            service.apply(10L, 20L, InboundReconcileApplyRequest(preview.previewToken, listOf("ext-1")))
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.CONFLICT)
        verify(exactly = 0) { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>(), any()) }
    }

    @Test
    fun `apply enqueues selected matched users without linking external ids`() {
        givenContributionTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"), ExternalMember("ext-2", "Mapped Two"))
        every { members.findAllByTargetIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
                ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-2"),
            )
        every { users.findAllByIds(setOf(1L, 2L)) } returns
            listOf(
                user(1L, "one@example.org"),
                user(2L, "two@example.org"),
            )
        every { writers.find(CohortType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.preview(any(), any()) } returns MembershipPreview(alreadyMember = false)
        every { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>(), any()) } returns
            TestJobExecution(55L)

        val preview = service.preview(10L, 20L)
        val result = service.apply(10L, 20L, InboundReconcileApplyRequest(preview.previewToken, listOf("ext-2")))

        assertThat(result.jobId).isEqualTo(55L)
        assertThat(result.acceptedCount).isEqualTo(1)
        assertThat(result.skippedCount).isEqualTo(1)
        verify {
            jobs.runAsync(
                CohortJobs.ApplyInboundReconcile,
                match<CohortJobs.ApplyInboundReconcilePayload> {
                    it.cohortId == 10L &&
                        it.targetId == 20L &&
                        it.system == TargetSystem.BREVO.name &&
                        it.externalTargetId == "list-20" &&
                        it.definitionKey == "PERIOD_PAYERS:12" &&
                        it.selected == listOf(CohortJobs.InboundReconcileSelectedUser("ext-2", 2L))
                },
                any(),
            )
        }
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
        verify {
            resolutions.record(20L, DriftResolutionAction.ADOPT, listOf(DriftResolutions.Person(2L, "ext-2", "Mapped Two")))
        }
    }

    @Test
    fun `apply job revalidates the mapping and writes in its own transaction`() {
        val given = givenContributionTarget()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            )
        every { writers.find(CohortType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.apply(1L, any()) } returns MembershipWriteStatus.WRITTEN

        val result =
            service.applyJob(
                CohortJobs.ApplyInboundReconcilePayload(
                    cohortId = given.cohort.id!!,
                    targetId = given.target.id!!,
                    system = TargetSystem.BREVO.name,
                    externalTargetId = "list-20",
                    definitionKey = "PERIOD_PAYERS:12",
                    selected = listOf(CohortJobs.InboundReconcileSelectedUser("ext-1", 1L)),
                ),
            )

        assertThat(result).containsExactly(ApplyInboundReconcileItemResult("ext-1", 1L, MembershipWriteStatus.WRITTEN))
        verify(exactly = 1) { contributionWriter.apply(1L, any()) }
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
    }

    @Test
    fun `apply job reports a cohort nothing can write into, without writing`() {
        val given = givenNewsletterTarget()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns
            listOf(
                ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            )
        every { writers.find(CohortType.NEWSLETTER_SUBSCRIBERS) } returns null

        val result =
            service.applyJob(
                CohortJobs.ApplyInboundReconcilePayload(
                    cohortId = given.cohort.id!!,
                    targetId = given.target.id!!,
                    system = TargetSystem.BREVO.name,
                    externalTargetId = "list-20",
                    definitionKey = "NEWSLETTER_SUBSCRIBERS",
                    selected = listOf(CohortJobs.InboundReconcileSelectedUser("ext-1", 1L)),
                ),
            )

        assertThat(result).containsExactly(ApplyInboundReconcileItemResult("ext-1", 1L, MembershipWriteStatus.UNSUPPORTED))
        verify(exactly = 0) { contributionWriter.apply(any(), any()) }
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
    }

    @Test
    fun `a target of another cohort, or a cohort whose definition is gone, cannot be adopted from`() {
        givenContributionTarget()
        val other = Target(system = TargetSystem.BREVO.name, kind = TargetKind.LIST, label = "Other", cohortId = 11L).apply { id = 21L }
        every { targets.findById(21L) } returns Optional.of(other)
        val old = Cohort(type = CohortType.PERIOD_PAYERS, label = "Old", definitionKey = "GONE").apply { id = 12L }
        every { cohorts.findById(12L) } returns Optional.of(old)
        every { targets.findById(22L) } returns
            Optional.of(Target(system = TargetSystem.BREVO.name, kind = TargetKind.LIST, label = "Old", cohortId = 12L).apply { id = 22L })
        every { definitions.byKey("GONE") } returns null

        val stranger = assertThrows<ResponseStatusException> { service.preview(10L, 21L) }
        val orphaned = assertThrows<ResponseStatusException> { service.preview(12L, 22L) }

        assertThat(stranger.reason).isEqualTo("Target 21 is not a target of cohort 10")
        assertThat(orphaned.reason).isEqualTo("Cohort 12 names no cohort in code any more")
    }

    private fun givenContributionTarget() = givenTarget(CohortType.PERIOD_PAYERS, "PERIOD_PAYERS:12", scope = 12L)

    private fun givenNewsletterTarget() = givenTarget(CohortType.NEWSLETTER_SUBSCRIBERS, "NEWSLETTER_SUBSCRIBERS", scope = null)

    private fun givenTarget(
        type: CohortType,
        key: String,
        scope: Long?,
    ): TargetFixture {
        val definition =
            mockk<CohortDefinition>(relaxed = true).also {
                every { it.key } returns key
                every { it.type } returns type
                every { it.scope } returns scope
                every { it.label } returns "Paid"
            }
        every { definitions.byKey(key) } returns definition
        val cohort =
            Cohort(
                type = type,
                label = "Paid",
                definitionKey = key,
            ).apply { id = 10L }
        val target =
            Target(
                system = TargetSystem.BREVO.name,
                kind = TargetKind.LIST,
                label = "Paid",
                cohortId = 10L,
                externalId = "list-20",
            ).apply { id = 20L }
        every { cohorts.findById(10L) } returns Optional.of(cohort)
        every { targets.findById(20L) } returns Optional.of(target)
        return TargetFixture(cohort, target)
    }

    private fun user(
        id: Long,
        email: String,
    ): User = Entities.user(id = id, firstName = "User", lastName = "$id", email = email)

    private class RecordingTargetStrategy : TargetStrategy {
        override fun memberIds(userIds: Set<Long>): Map<Long, String> = emptyMap()

        override fun ownersOf(externalUserIds: Set<String>): Map<String, Long> = emptyMap()

        override val makesMemberIds = false

        override fun makeMemberId(userId: Long) = Unit

        override val descriptor =
            TargetDescriptor(
                system = TargetSystem.BREVO,
                kind = TargetKind.LIST,
            )
        var remote: List<ExternalMember> = emptyList()
        var listCalls = 0
        var sawTransactionDuringMembers = false

        override fun members(external: ExternalTarget): List<ExternalMember> {
            listCalls += 1
            sawTransactionDuringMembers = TransactionSynchronizationManager.isActualTransactionActive()
            return remote
        }

        override fun add(
            external: ExternalTarget,
            externalUserId: String,
        ) = error("not used")

        override fun remove(
            external: ExternalTarget,
            externalUserId: String,
        ) = error("not used")

        override fun create(
            label: String,
            folder: String?,
        ): ExternalTarget = error("not used")

        override fun delete(external: ExternalTarget) = error("not used")

        override fun rename(
            external: ExternalTarget,
            name: String,
        ): ExternalTarget = error("not used")

        override fun createFolder(name: String): List<String> = error("not used")
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

    private data class TargetFixture(
        val cohort: Cohort,
        val target: Target,
    )

    private data class TestJobExecution(
        override val id: Long?,
    ) : QueuedJob {
        override val jobType: String = "test"
        override val payload: String? = null
        override val actor =
            net.blueshell.api.shared.tracking.Actor
                .system()
    }
}
