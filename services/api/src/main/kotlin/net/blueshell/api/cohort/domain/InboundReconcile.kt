package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.DUPLICATE_REMOTE_ID
import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.DUPLICATE_USER_MATCH
import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.MAPPED_USER_INACTIVE
import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.MAPPING_CONFLICT
import net.blueshell.api.cohort.domain.InboundReconcileSkipReason.UNMATCHED
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
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
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val members: TargetMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
    private val writers: MembershipWriters,
    private val definitions: CohortDefinitionRegistry,
    private val jobs: JobQueue,
    private val strategies: TargetStrategies,
    private val resolutions: DriftResolutions,
    transactionManager: PlatformTransactionManager,
) {
    private val noTx = transactionManager.tx(TransactionDefinition.PROPAGATION_NOT_SUPPORTED)
    private val itemTx = transactionManager.tx(TransactionDefinition.PROPAGATION_REQUIRES_NEW)

    fun preview(cohortId: Long, targetId: Long): InboundReconcilePreview =
        preview(loadTarget(cohortId, targetId))

    private fun preview(inbound: InboundTarget): InboundReconcilePreview {
        val remote = noTx.execute { strategies.require(inbound.system).members(inbound.external) }.orEmpty()
        val (matched, skipped) = match(inbound, remote)
        val preview = InboundReconcilePreview(
            inbound.definition.key, inbound.definition.label, inbound.writer != null, "", remote.size, matched, skipped,
        )
        return preview.copy(previewToken = token(inbound, preview))
    }

    fun apply(cohortId: Long, targetId: Long, request: InboundReconcileApplyRequest): InboundReconcileApplyResponse {
        val inbound = loadTarget(cohortId, targetId)
        val current = preview(inbound)
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
            inbound.cohortId, inbound.targetId, inbound.system.name, inbound.external.externalId,
            inbound.definition.key, selected,
        )
        val skipped = current.skipped.size + current.matched.size - selected.size
        val queued = jobs.runAsync(CohortJobs.ApplyInboundReconcile, payload, JobTrigger.SITE_ACTION)
        resolutions.record(
            inbound.targetId,
            DriftResolutionAction.ADOPT,
            selected.map { DriftResolutions.Person(it.userId, it.externalUserId, byExternalId[it.externalUserId]?.externalLabel) },
        )
        return InboundReconcileApplyResponse(queued?.id, selected.size, skipped)
    }

    fun applyJob(payload: CohortJobs.ApplyInboundReconcilePayload): List<ApplyInboundReconcileItemResult> {
        val definition = definitions.byKey(payload.definitionKey)
            ?: bad("${payload.definitionKey} names no cohort any more")
        return payload.selected.map { selected ->
            val status = itemTx.execute { applyOne(payload, definition, selected) }
            ApplyInboundReconcileItemResult(selected.externalUserId, selected.userId, status)
        }
    }

    private fun loadTarget(cohortId: Long, targetId: Long): InboundTarget {
        val cohort = cohorts.findById(cohortId)
            .orElseThrow { missing("Cohort $cohortId not found") }
        val target = targets.findById(targetId)
            .orElseThrow { missing("Target $targetId not found") }
        if (target.cohortId != cohortId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target $targetId is not a target of cohort $cohortId")
        }
        val definition = cohort.definitionKey?.let { definitions.byKey(it) }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cohort $cohortId names no cohort in code any more")
        val system = TargetSystem.valueOf(target.system)
        val externalId = target.externalId.required("Target $targetId has no external target")
        return InboundTarget(
            cohortId, targetId, system,
            ExternalTarget(system, externalId, target.kind, target.label, target.folder),
            definition, writers.find(definition.type),
        )
    }

    private fun applyOne(
        payload: CohortJobs.ApplyInboundReconcilePayload,
        definition: CohortDefinition,
        selected: CohortJobs.InboundReconcileSelectedUser,
    ): MembershipWriteStatus {
        val inbound = loadTarget(payload.cohortId, payload.targetId)
        if (!inbound.matches(payload, definition)) return MembershipWriteStatus.FAILED
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

    private fun match(inbound: InboundTarget, remote: List<ExternalMember>): Pair<List<InboundReconcileRow>, List<InboundReconcileRow>> {
        val duplicateIds = remote.groupingBy { it.externalUserId }.eachCount().filterValues { it > 1 }.keys
        val skipped = duplicateIds.map { remote.first { member -> member.externalUserId == it }.skip(DUPLICATE_REMOTE_ID) }.toMutableList()
        val extras = remote.filterNot { it.externalUserId in duplicateIds || it.externalUserId in internalExternalIds(inbound) }
        val mappings = mappings(inbound, extras)
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
                else -> matched += inbound.row(member, user)
            }
        }
        return matched to skipped
    }

    private fun internalExternalIds(inbound: InboundTarget): Set<String> {
        val internalIds = members.findAllByTargetIdAndUserIdIsNotNull(inbound.targetId).mapNotNull { it.userId }.toSet()
        return externalIds.findBatch(ExternalIdMappingService.USER_AGGREGATE, internalIds, inbound.system.name)
            .mapNotNull { it.externalId?.takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun mappings(inbound: InboundTarget, members: List<ExternalMember>) =
        externalIds.findByExternalIds(
            ExternalIdMappingService.USER_AGGREGATE,
            inbound.system.name,
            members.map { it.externalUserId },
        ).filter { !it.externalId.isNullOrBlank() }.groupBy { it.externalId!! }

    private fun InboundTarget.row(member: ExternalMember, user: User): InboundReconcileRow {
        val alreadyMember = writer?.preview(user.id!!, definition)?.alreadyMember ?: false
        return InboundReconcileRow(
            member.externalUserId,
            member.label,
            user.id!!,
            user.fullName,
            user.email,
            alreadyMember,
            writer != null && !alreadyMember,
        )
    }

    private fun token(inbound: InboundTarget, preview: InboundReconcilePreview): String {
        val rows = preview.matched.sortedBy { it.externalUserId }
            .joinToString(",") { "${it.externalUserId}=${it.userId}:${it.writable}" }
        val input =
            listOf(inbound.cohortId, inbound.targetId, inbound.system, inbound.external.externalId, inbound.definition.key, rows)
                .joinToString("|")
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
    val cohortId: Long,
    val targetId: Long,
    val system: TargetSystem,
    val external: ExternalTarget,
    val definition: CohortDefinition,
    val writer: MembershipWriter?,
)

data class InboundReconcilePreview(
    val definitionKey: String,
    val cohortLabel: String,
    val writerSupported: Boolean,
    val previewToken: String,
    val remoteCount: Int,
    val matched: List<InboundReconcileRow>,
    val skipped: List<InboundReconcileRow>,
)

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
