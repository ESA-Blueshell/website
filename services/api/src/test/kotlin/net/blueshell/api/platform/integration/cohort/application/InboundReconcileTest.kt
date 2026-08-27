package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinition
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinitionRegistry
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.out.ExternalMember
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.platform.integration.cohort.application.CohortJobs
import net.blueshell.api.shared.job.JobExecution
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class InboundReconcileTest {
    private val subjects: CohortSubjectRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val members: CohortMemberRepository = mockk()
    private val externalIds: ExternalIdMappingService = mockk(relaxed = true)
    private val users: UserService = mockk()
    private val writers: MembershipWriters = mockk()
    private val contributionWriter: MembershipWriter = mockk()
    private val definitions: CohortDefinitionRegistry = mockk()
    private val jobs: TrackedJobDispatcher = mockk(relaxed = true)
    private val strategy = RecordingTargetStrategy()
    private val service = InboundReconcile(
        subjects = subjects,
        cohorts = cohorts,
        members = members,
        externalIds = externalIds,
        users = users,
        writers = writers,
        definitions = definitions,
        jobs = jobs,
        strategies = TargetStrategies(listOf(strategy)),
        transactionManager = ImmediateTransactionManager(),
    )

    @Test
    fun `preview is write-free external-id only and classifies duplicate conflict inactive and unmatched`() {
        val target = givenContributionTarget()
        val desired = CohortMember(target.cohort, userId = 9L, subject = target.subject)
        strategy.remote = listOf(
            ExternalMember("ext-internal", "Already internal"),
            ExternalMember("ext-1", "Mapped One"),
            ExternalMember("ext-dup", "Duplicate A"),
            ExternalMember("ext-dup", "Duplicate B"),
            ExternalMember("ext-conflict", "Conflict"),
            ExternalMember("ext-missing", "Missing"),
            ExternalMember("ext-inactive", "Inactive"),
        )
        every { members.findAllByCohortIdAndUserIdIsNotNull(20L) } returns listOf(desired)
        every { externalIds.findBatch("USER", setOf(9L), TargetSystem.BREVO.name) } returns listOf(
            ExternalIdMapping("USER", 9L, TargetSystem.BREVO.name, "ext-internal"),
        )
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-conflict"),
            ExternalIdMapping("USER", 3L, TargetSystem.BREVO.name, "ext-conflict"),
            ExternalIdMapping("USER", 4L, TargetSystem.BREVO.name, "ext-inactive"),
        )
        every { users.findAllByIds(setOf(1L, 2L, 3L, 4L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortSubjectType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.preview(1L, any()) } returns MembershipPreview(alreadyMember = false)

        val preview = service.preview(10L, 20L)

        assertThat(strategy.listCalls).isEqualTo(1)
        assertThat(strategy.sawTransactionDuringMembers).isFalse()
        assertThat(preview.writerSupported).isTrue()
        assertThat(preview.matched).extracting<Long?> { it.userId }.containsExactly(1L)
        assertThat(preview.matched.single().writable).isTrue()
        assertThat(preview.skipped).extracting<InboundReconcileSkipReason> { it.reason }
            .containsExactlyInAnyOrder(
                InboundReconcileSkipReason.DUPLICATE_REMOTE_ID,
                InboundReconcileSkipReason.MAPPING_CONFLICT,
                InboundReconcileSkipReason.UNMATCHED,
                InboundReconcileSkipReason.MAPPED_USER_INACTIVE,
            )
        assertThat(preview.matched.map { it.externalUserId }).doesNotContain("ext-internal")
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
        verify(exactly = 0) { contributionWriter.apply(any(), any()) }
        verify(exactly = 0) { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>()) }
    }

    @Test
    fun `preview returns disabled writable rows when a cohort cannot be written into`() {
        givenNewsletterTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"))
        every { members.findAllByCohortIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
        )
        every { users.findAllByIds(setOf(1L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortSubjectType.NEWSLETTER_SUBSCRIBERS) } returns null

        val preview = service.preview(10L, 20L)

        assertThat(preview.writerSupported).isFalse()
        assertThat(preview.matched.single().writable).isFalse()
        assertThat(preview.matched.single().alreadyMember).isFalse()
    }

    @Test
    fun `apply rejects stale preview token before enqueueing`() {
        givenContributionTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"))
        every { members.findAllByCohortIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
        )
        every { users.findAllByIds(setOf(1L)) } returns listOf(user(1L, "mapped@example.org"))
        every { writers.find(CohortSubjectType.PERIOD_PAYERS) } returns contributionWriter
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
        verify(exactly = 0) { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>()) }
    }

    @Test
    fun `apply enqueues selected matched users without linking external ids`() {
        givenContributionTarget()
        strategy.remote = listOf(ExternalMember("ext-1", "Mapped One"), ExternalMember("ext-2", "Mapped Two"))
        every { members.findAllByCohortIdAndUserIdIsNotNull(20L) } returns emptyList()
        every { externalIds.findBatch("USER", emptySet<Long>(), TargetSystem.BREVO.name) } returns emptyList()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, any()) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
            ExternalIdMapping("USER", 2L, TargetSystem.BREVO.name, "ext-2"),
        )
        every { users.findAllByIds(setOf(1L, 2L)) } returns listOf(
            user(1L, "one@example.org"),
            user(2L, "two@example.org"),
        )
        every { writers.find(CohortSubjectType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.preview(any(), any()) } returns MembershipPreview(alreadyMember = false)
        every { jobs.runAsync(CohortJobs.ApplyInboundReconcile, any<CohortJobs.ApplyInboundReconcilePayload>()) } returns
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
                    it.subjectId == 10L &&
                        it.cohortId == 20L &&
                        it.system == TargetSystem.BREVO.name &&
                        it.externalTargetId == "list-20" &&
                        it.definitionKey == "PERIOD_PAYERS:12" &&
                        it.selected == listOf(CohortJobs.InboundReconcileSelectedUser("ext-2", 2L))
                },
            )
        }
        verify(exactly = 0) { externalIds.linkUser(any(), any(), any()) }
    }

    @Test
    fun `apply job revalidates the mapping and writes in its own transaction`() {
        val target = givenContributionTarget()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
        )
        every { writers.find(CohortSubjectType.PERIOD_PAYERS) } returns contributionWriter
        every { contributionWriter.apply(1L, any()) } returns MembershipWriteStatus.WRITTEN

        val result = service.applyJob(
            CohortJobs.ApplyInboundReconcilePayload(
                subjectId = target.subject.id!!,
                cohortId = target.cohort.id!!,
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
        val target = givenNewsletterTarget()
        every { externalIds.findByExternalIds("USER", TargetSystem.BREVO.name, listOf("ext-1")) } returns listOf(
            ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"),
        )
        every { writers.find(CohortSubjectType.NEWSLETTER_SUBSCRIBERS) } returns null

        val result = service.applyJob(
            CohortJobs.ApplyInboundReconcilePayload(
                subjectId = target.subject.id!!,
                cohortId = target.cohort.id!!,
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

    private fun givenContributionTarget() =
        givenTarget(CohortSubjectType.PERIOD_PAYERS, "PERIOD_PAYERS:12", scope = 12L)

    private fun givenNewsletterTarget() =
        givenTarget(CohortSubjectType.NEWSLETTER_SUBSCRIBERS, "NEWSLETTER_SUBSCRIBERS", scope = null)

    private fun givenTarget(type: CohortSubjectType, key: String, scope: Long?): TargetFixture {
        val definition = mockk<CohortDefinition>(relaxed = true).also {
            every { it.key } returns key
            every { it.type } returns type
            every { it.scope } returns scope
            every { it.label } returns "Subject"
        }
        every { definitions.byKey(key) } returns definition
        val subject = CohortSubject(
            type = type,
            label = "Subject",
            definitionKey = key,
        ).apply { id = 10L }
        val cohort = Cohort(
            system = TargetSystem.BREVO.name,
            kind = CohortKind.LIST,
            label = "Subject",
            subjectId = 10L,
            externalId = "list-20",
        ).apply { id = 20L }
        every { subjects.findById(10L) } returns Optional.of(subject)
        every { cohorts.findById(20L) } returns Optional.of(cohort)
        return TargetFixture(subject, cohort)
    }

    private fun user(id: Long, email: String): User {
        val user = mockk<User>()
        every { user.id } returns id
        every { user.fullName } returns "User $id"
        every { user.email } returns email
        return user
    }

    private class RecordingTargetStrategy : TargetStrategy {
        override val descriptor = TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            systemLabel = "Brevo",
            targetLabel = "Brevo list",
            idLabel = "List id",
            capabilities = setOf(TargetCapability.READ_MEMBERS),
        )
        var remote: List<ExternalMember> = emptyList()
        var listCalls = 0
        var sawTransactionDuringMembers = false

        override fun members(target: ExternalTarget): List<ExternalMember> {
            listCalls += 1
            sawTransactionDuringMembers = TransactionSynchronizationManager.isActualTransactionActive()
            return remote
        }

        override fun add(target: ExternalTarget, externalUserId: String) = error("not used")
        override fun remove(target: ExternalTarget, externalUserId: String) = error("not used")
        override fun create(label: String, folder: String?): ExternalTarget = error("not used")
        override fun delete(target: ExternalTarget) = error("not used")
    }

    private class ImmediateTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }

    private data class TargetFixture(val subject: CohortSubject, val cohort: Cohort)

    private data class TestJobExecution(override val id: Long?) : JobExecution {
        override val jobType: String = "test"
        override val payload: String? = null
        override val actor = net.blueshell.api.shared.tracking.Actor.system()
    }
}
