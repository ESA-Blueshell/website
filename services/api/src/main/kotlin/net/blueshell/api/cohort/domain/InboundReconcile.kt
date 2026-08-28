package net.blueshell.api.cohort.domain

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.*
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Service
class InboundReconcile(
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val members: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
    private val writers: MembershipWriters,
    private val definitions: CohortDefinitionRegistry,
    private val jobs: TrackedJobDispatcher,
    private val strategies: TargetStrategies,
    transactionManager: PlatformTransactionManager,
) {
    private val noTx = transactionManager.tx(TransactionDefinition.PROPAGATION_NOT_SUPPORTED)
    private val itemTx = transactionManager.tx(TransactionDefinition.PROPAGATION_REQUIRES_NEW)

    fun preview(subjectId: Long, cohortId: Long): InboundReconcilePreview =
        preview(loadTarget(subjectId, cohortId))

    private fun preview(target: InboundTarget): InboundReconcilePreview {
        val remote = noTx.execute { strategies.require(target.system).members(target.external) }.orEmpty()
        val (matched, skipped) = match(target, remote)
        val preview = InboundReconcilePreview(
            target.definition.key, target.definition.label, target.writer != null, "", remote.size, matched, skipped,
        )
        return preview.copy(previewToken = token(target, preview))
    }

    fun apply(subjectId: Long, cohortId: Long, request: InboundReconcileApplyRequest): InboundReconcileApplyResponse {
        val target = loadTarget(subjectId, cohortId)
        val current = preview(target)
        if (current.previewToken != request.previewToken) {
            conflict("Inbound reconcile preview is stale")
        }
        val selectedIds = request.selectedExternalUserIds.toSet()
        val byExternalId = current.matched.associateBy { it.externalUserId }
        val selected = selectedIds.map {
            val row = byExternalId[it]
                ?: conflict("Selected external user $it is no longer matched")
            if (!row.writable) bad("Selected external user $it is not writable")
            CohortJobs.InboundReconcileSelectedUser(row.externalUserId, row.userId!!)
        }
        val payload = CohortJobs.ApplyInboundReconcilePayload(
            target.subjectId, target.cohortId, target.system.name, target.external.externalId,
            target.definition.key, selected,
        )
        val skipped = current.skipped.size + current.matched.size - selected.size
        return InboundReconcileApplyResponse(jobs.runAsync(CohortJobs.ApplyInboundReconcile, payload)?.id, selected.size, skipped)
    }

    fun applyJob(payload: CohortJobs.ApplyInboundReconcilePayload): List<ApplyInboundReconcileItemResult> {
        val definition = definitions.byKey(payload.definitionKey)
            ?: bad("${payload.definitionKey} names no cohort any more")
        return payload.selected.map { selected ->
            val status = itemTx.execute { applyOne(payload, definition, selected) }
            ApplyInboundReconcileItemResult(selected.externalUserId, selected.userId, status)
        }
    }

    private fun loadTarget(subjectId: Long, cohortId: Long): InboundTarget {
        val subject = subjects.findById(subjectId)
            .orElseThrow { missing("Subject $subjectId not found") }
        val cohort = cohorts.findById(cohortId)
            .orElseThrow { missing("Cohort $cohortId not found") }
        if (cohort.subjectId != subjectId) {
            missing("Cohort $cohortId is not a target of subject $subjectId")
        }
        val definition = subject.definitionKey?.let { definitions.byKey(it) }
            ?: bad("Subject $subjectId names no cohort in code any more")
        val system = TargetSystem.valueOf(cohort.system)
        val externalId = cohort.externalId.required("Cohort $cohortId has no external target")
        return InboundTarget(
            subjectId, cohortId, system,
            ExternalTarget(system, externalId, cohort.kind, cohort.label, cohort.folder),
            definition, writers.find(definition.type),
        )
    }

    private fun applyOne(
        payload: CohortJobs.ApplyInboundReconcilePayload,
        definition: CohortDefinition,
        selected: CohortJobs.InboundReconcileSelectedUser,
    ): MembershipWriteStatus {
        val target = loadTarget(payload.subjectId, payload.cohortId)
        if (!target.matches(payload, definition)) return MembershipWriteStatus.FAILED
        val mappings = externalIds.findByExternalIds(
            ExternalIdMappingService.USER_AGGREGATE,
            payload.system,
            listOf(selected.externalUserId),
        ).filter { it.externalId == selected.externalUserId }
        val mappedUserId = mappings.singleOrNull()?.aggregateId
            ?: return if (mappings.isEmpty()) MembershipWriteStatus.SKIPPED_UNMATCHED else MembershipWriteStatus.SKIPPED_MAPPING_CONFLICT
        if (mappedUserId != selected.userId) return MembershipWriteStatus.SKIPPED_MAPPING_CONFLICT
        val writer = writers.find(definition.type) ?: return MembershipWriteStatus.UNSUPPORTED
        return runCatching { writer.apply(selected.userId, definition) }
            .getOrElse { MembershipWriteStatus.FAILED }
    }

    private fun match(target: InboundTarget, remote: List<ExternalMember>): Pair<List<InboundReconcileRow>, List<InboundReconcileRow>> {
        val duplicateIds = remote.groupingBy { it.externalUserId }.eachCount().filterValues { it > 1 }.keys
        val skipped = duplicateIds.map { remote.first { member -> member.externalUserId == it }.skip(DUPLICATE_REMOTE_ID) }.toMutableList()
        val extras = remote.filterNot { it.externalUserId in duplicateIds || it.externalUserId in internalExternalIds(target) }
        val mappings = mappings(target, extras)
        val activeUsers = users.findAllByIds(mappings.values.flatten().map { it.aggregateId }.toSet()).associateBy { it.id!! }
        val matched = mutableListOf<InboundReconcileRow>()
        val seenUsers = mutableSetOf<Long>()
        extras.forEach { member ->
            val memberMappings = mappings[member.externalUserId].orEmpty()
            val mappedIds = memberMappings.map { it.aggregateId }.toSet()
            val user = mappedIds.singleOrNull()?.let(activeUsers::get)
            when {
                memberMappings.isEmpty() -> skipped += member.skip(UNMATCHED)
                mappedIds.size != 1 -> skipped += member.skip(MAPPING_CONFLICT)
                user == null -> skipped += member.skip(MAPPED_USER_INACTIVE)
                !seenUsers.add(user.id!!) -> skipped += member.skip(DUPLICATE_USER_MATCH)
                else -> matched += target.row(member, user)
            }
        }
        return matched to skipped
    }

    private fun internalExternalIds(target: InboundTarget): Set<String> {
        val internalIds = members.findAllByCohortIdAndUserIdIsNotNull(target.cohortId).mapNotNull { it.userId }.toSet()
        return externalIds.findBatch(ExternalIdMappingService.USER_AGGREGATE, internalIds, target.system.name)
            .mapNotNull { it.externalId?.takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun mappings(target: InboundTarget, members: List<ExternalMember>) =
        externalIds.findByExternalIds(
            ExternalIdMappingService.USER_AGGREGATE,
            target.system.name,
            members.map { it.externalUserId },
        ).filter { !it.externalId.isNullOrBlank() }.groupBy { it.externalId!! }

    private fun InboundTarget.row(member: ExternalMember, user: User): InboundReconcileRow {
        val alreadyMember = writer?.preview(user.id!!, definition)?.alreadyMember ?: false
        return InboundReconcileRow(member.externalUserId, member.label, user.id!!, user.fullName, user.email, alreadyMember, writer != null && !alreadyMember)
    }

    private fun token(target: InboundTarget, preview: InboundReconcilePreview): String {
        val rows = preview.matched.sortedBy { it.externalUserId }
            .joinToString(",") { "${it.externalUserId}=${it.userId}:${it.writable}" }
        val input = "${target.subjectId}|${target.cohortId}|${target.system}|${target.external.externalId}|${target.definition.key}|$rows"
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun InboundTarget.matches(payload: CohortJobs.ApplyInboundReconcilePayload, definition: CohortDefinition) =
        system.name == payload.system &&
            external.externalId == payload.externalTargetId &&
            this.definition.key == definition.key

    private fun ExternalMember.skip(reason: InboundReconcileSkipReason) =
        InboundReconcileRow(externalUserId = externalUserId, externalLabel = label, reason = reason)

    private fun String?.required(message: String) = takeIf { !it.isNullOrBlank() } ?: bad(message)
    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
    private fun missing(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
    private fun PlatformTransactionManager.tx(propagation: Int) =
        TransactionTemplate(this).apply { propagationBehavior = propagation }
}

private data class InboundTarget(
    val subjectId: Long,
    val cohortId: Long,
    val system: TargetSystem,
    val external: ExternalTarget,
    val definition: CohortDefinition,
    val writer: MembershipWriter?,
)

data class InboundReconcilePreview(val definitionKey: String, val cohortLabel: String, val writerSupported: Boolean, val previewToken: String, val remoteCount: Int, val matched: List<InboundReconcileRow>, val skipped: List<InboundReconcileRow>)

data class InboundReconcileRow(
    val externalUserId: String,
    val externalLabel: String? = null,
    val userId: Long? = null,
    val userFullName: String? = null,
    val userEmail: String? = null,
    val alreadyMember: Boolean = false,
    val writable: Boolean = false,
    val reason: InboundReconcileSkipReason? = null,
)

enum class InboundReconcileSkipReason { DUPLICATE_REMOTE_ID, MAPPING_CONFLICT, DUPLICATE_USER_MATCH, MAPPED_USER_INACTIVE, UNMATCHED }

data class InboundReconcileApplyRequest(val previewToken: String, val selectedExternalUserIds: List<String>)
data class InboundReconcileApplyResponse(val jobId: Long?, val acceptedCount: Int, val skippedCount: Int)
data class ApplyInboundReconcileItemResult(val externalUserId: String, val userId: Long, val status: MembershipWriteStatus)
