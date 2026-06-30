package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.out.ExternalMember
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

@Service
class InboundReconcile(
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val members: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
    private val writers: FactWriters,
    private val jobs: TrackedJobDispatcher,
    private val strategies: TargetStrategies,
    transactionManager: PlatformTransactionManager,
) {
    private val noTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }
    private val itemTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun preview(subjectId: Long, cohortId: Long): InboundReconcilePreview {
        val target = loadTarget(subjectId, cohortId)
        val remote = noTx.execute { strategies.require(target.system).members(target.external) }.orEmpty()
        val matched = match(target, remote)
        val preview = InboundReconcilePreview(
            subjectId = subjectId,
            cohortId = cohortId,
            system = target.system,
            externalTargetId = target.external.externalId,
            fact = target.fact,
            writerSupported = target.writer != null,
            previewToken = "",
            fetchedAt = Instant.now(),
            remoteCount = remote.size,
            matched = matched.matched,
            skipped = matched.skipped,
        )
        return preview.copy(previewToken = token(preview))
    }

    fun apply(subjectId: Long, cohortId: Long, request: InboundReconcileApplyRequest): InboundReconcileApplyResponse {
        val current = preview(subjectId, cohortId)
        if (current.previewToken != request.previewToken) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Inbound reconcile preview is stale")
        }
        val selectedIds = request.selectedExternalUserIds.toSet()
        val byExternalId = current.matched.associateBy { it.externalUserId }
        val selected = selectedIds.map {
            val row = byExternalId[it]
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Selected external user $it is no longer matched")
            if (!row.writable) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected external user $it is not writable")
            CohortJobs.InboundReconcileSelectedUser(row.externalUserId, row.userId)
        }
        val job = jobs.enqueue(
            CohortJobs.ApplyInboundReconcile,
            CohortJobs.ApplyInboundReconcilePayload(
                subjectId = current.subjectId,
                cohortId = current.cohortId,
                system = current.system.name,
                externalTargetId = current.externalTargetId,
                factKind = current.fact.kind.name,
                factKey = current.fact.key,
                selected = selected,
            ),
        )
        return InboundReconcileApplyResponse(job?.id, selected.size, current.skipped.size + current.matched.size - selected.size)
    }

    fun applyJob(payload: CohortJobs.ApplyInboundReconcilePayload): List<ApplyInboundReconcileItemResult> {
        val factKind = CohortFactKind.valueOf(payload.factKind)
        val fact = SubjectFact(factKind, payload.factKey)
        return payload.selected.map { selected ->
            val status = itemTx.execute {
                val target = loadTarget(payload.subjectId, payload.cohortId)
                if (target.system.name != payload.system ||
                    target.external.externalId != payload.externalTargetId ||
                    target.fact != fact
                ) {
                    return@execute FactWriteStatus.FAILED
                }
                val mappings = externalIds.findByExternalIds(
                    ExternalIdMappingService.USER_AGGREGATE,
                    payload.system,
                    listOf(selected.externalUserId),
                ).filter { it.externalId == selected.externalUserId }
                if (mappings.isEmpty()) return@execute FactWriteStatus.SKIPPED_UNMATCHED
                if (mappings.size != 1 || mappings.single().aggregateId != selected.userId) {
                    return@execute FactWriteStatus.SKIPPED_MAPPING_CONFLICT
                }
                val writer = writers.find(factKind) ?: return@execute FactWriteStatus.UNSUPPORTED
                runCatching { writer.apply(selected.userId, fact).status }
                    .getOrElse { FactWriteStatus.FAILED }
            }
            ApplyInboundReconcileItemResult(selected.externalUserId, selected.userId, status)
        }
    }

    @Transactional(readOnly = true)
    fun loadTarget(subjectId: Long, cohortId: Long): InboundReconcileTarget {
        val subject = subjects.findById(subjectId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found") }
        val cohort = cohorts.findById(cohortId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found") }
        if (cohort.subjectId != subjectId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId is not a target of subject $subjectId")
        }
        if (!subject.enabled) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject $subjectId is disabled")
        val fact = SubjectFact(
            subject.factKind ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject $subjectId has no fact kind"),
            subject.factKey?.takeIf { it.isNotBlank() }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject $subjectId has no fact key"),
        )
        val system = TargetSystem.valueOf(cohort.system)
        val externalId = cohort.externalId?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cohort $cohortId has no external target")
        return InboundReconcileTarget(
            subject = subject,
            cohort = cohort,
            system = system,
            external = ExternalTarget(system, externalId, cohort.kind, cohort.label, cohort.folder),
            fact = fact,
            writer = writers.find(fact.kind),
        )
    }

    private fun match(target: InboundReconcileTarget, remote: List<ExternalMember>): MatchRows {
        val duplicateRemoteIds = remote.groupingBy { it.externalUserId }.eachCount().filterValues { it > 1 }.keys
        val skipped = duplicateRemoteIds.map {
            InboundReconcileSkippedRow(it, remote.firstOrNull { member -> member.externalUserId == it }?.label, InboundReconcileSkipReason.DUPLICATE_REMOTE_ID)
        }.toMutableList()
        val internalIds = members.findAllByCohortIdAndUserIdIsNotNull(target.cohort.id!!).mapNotNull { it.userId }.toSet()
        val internalExternalIds = externalIds.findBatch(ExternalIdMappingService.USER_AGGREGATE, internalIds, target.system.name)
            .mapNotNull { it.externalId?.takeIf(String::isNotBlank) }
            .toSet()
        val extras = remote
            .filterNot { it.externalUserId in duplicateRemoteIds }
            .filterNot { it.externalUserId in internalExternalIds }
        val mappings = externalIds.findByExternalIds(
            ExternalIdMappingService.USER_AGGREGATE,
            target.system.name,
            extras.map { it.externalUserId },
        ).filter { !it.externalId.isNullOrBlank() }
            .groupBy { it.externalId!! }
        val activeUsers = users.findAllByIds(mappings.values.flatten().map { it.aggregateId }.toSet())
            .associateBy { it.id!! }
        val matched = mutableListOf<InboundReconcileMatchedRow>()
        val seenUsers = mutableSetOf<Long>()
        extras.forEach { member ->
            val memberMappings = mappings[member.externalUserId].orEmpty()
            when {
                memberMappings.isEmpty() -> skipped += member.skip(InboundReconcileSkipReason.UNMATCHED)
                memberMappings.map { it.aggregateId }.toSet().size != 1 ->
                    skipped += member.skip(InboundReconcileSkipReason.MAPPING_CONFLICT)
                activeUsers[memberMappings.single().aggregateId] == null ->
                    skipped += member.skip(InboundReconcileSkipReason.MAPPED_USER_INACTIVE)
                !seenUsers.add(memberMappings.single().aggregateId) ->
                    skipped += member.skip(InboundReconcileSkipReason.DUPLICATE_USER_MATCH)
                else -> matched += target.row(member, activeUsers.getValue(memberMappings.single().aggregateId))
            }
        }
        return MatchRows(matched, skipped)
    }

    private fun InboundReconcileTarget.row(member: ExternalMember, user: User): InboundReconcileMatchedRow {
        val alreadyTrue = writer?.preview(user.id!!, fact)?.alreadyTrue ?: false
        return InboundReconcileMatchedRow(
            externalUserId = member.externalUserId,
            externalLabel = member.label,
            userId = user.id!!,
            userFullName = user.fullName,
            userEmail = user.email,
            alreadyTrue = alreadyTrue,
            writable = writer != null && !alreadyTrue,
        )
    }

    private fun token(preview: InboundReconcilePreview): String {
        val input = buildString {
            append(preview.subjectId).append('|').append(preview.cohortId).append('|')
            append(preview.system).append('|').append(preview.externalTargetId).append('|')
            append(preview.fact.kind).append('|').append(preview.fact.key).append('|')
            preview.matched.sortedBy { it.externalUserId }.forEach {
                append(it.externalUserId).append('=').append(it.userId).append(':').append(it.writable).append(',')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun ExternalMember.skip(reason: InboundReconcileSkipReason) =
        InboundReconcileSkippedRow(externalUserId, label, reason)
}

data class InboundReconcileTarget(
    val subject: CohortSubject,
    val cohort: Cohort,
    val system: TargetSystem,
    val external: ExternalTarget,
    val fact: SubjectFact,
    val writer: FactWriter?,
)

data class InboundReconcilePreview(
    val subjectId: Long,
    val cohortId: Long,
    val system: TargetSystem,
    val externalTargetId: String,
    val fact: SubjectFact,
    val writerSupported: Boolean,
    val previewToken: String,
    val fetchedAt: Instant,
    val remoteCount: Int,
    val matched: List<InboundReconcileMatchedRow>,
    val skipped: List<InboundReconcileSkippedRow>,
)

data class InboundReconcileMatchedRow(
    val externalUserId: String,
    val externalLabel: String?,
    val userId: Long,
    val userFullName: String?,
    val userEmail: String?,
    val alreadyTrue: Boolean,
    val writable: Boolean,
)

data class InboundReconcileSkippedRow(
    val externalUserId: String,
    val externalLabel: String?,
    val reason: InboundReconcileSkipReason,
)

enum class InboundReconcileSkipReason {
    DUPLICATE_REMOTE_ID,
    MAPPING_CONFLICT,
    DUPLICATE_USER_MATCH,
    MAPPED_USER_INACTIVE,
    UNMATCHED,
}

data class InboundReconcileApplyRequest(val previewToken: String, val selectedExternalUserIds: List<String>)
data class InboundReconcileApplyResponse(val jobId: Long?, val acceptedCount: Int, val skippedCount: Int)
data class ApplyInboundReconcileItemResult(val externalUserId: String, val userId: Long, val status: FactWriteStatus)

private data class MatchRows(
    val matched: List<InboundReconcileMatchedRow>,
    val skipped: List<InboundReconcileSkippedRow>,
)
