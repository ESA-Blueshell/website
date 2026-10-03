package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.cohort.domain.CohortDetail
import net.blueshell.api.cohort.domain.CohortQueryService
import net.blueshell.api.cohort.domain.CohortSummary
import net.blueshell.api.cohort.domain.CohortTargetRow
import net.blueshell.api.cohort.domain.CohortTargeting
import net.blueshell.api.cohort.domain.DriftResolutions
import net.blueshell.api.cohort.domain.InboundReconcile
import net.blueshell.api.cohort.domain.InboundReconcileApplyRequest
import net.blueshell.api.cohort.domain.InboundReconcileApplyResponse
import net.blueshell.api.cohort.domain.InboundReconcilePreview
import net.blueshell.api.cohort.domain.LinkChoice
import net.blueshell.api.cohort.domain.LinkOutcome
import net.blueshell.api.cohort.domain.LinkProposal
import net.blueshell.api.cohort.domain.SummaryTarget
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetMemberRow
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.security.AdminOnly
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The cohorts, each with the targets that mirror it on the external systems, and their drift. */
@RestController
@RequestMapping("/management/cohorts")
@Tag(name = "Cohorts", description = "The board's cohorts and the targets that mirror them")
@BoardOnly
class CohortController(
    private val queries: CohortQueryService,
    private val resolutions: DriftResolutions,
    private val targeting: CohortTargeting,
    private val inboundReconcile: InboundReconcile,
    private val catalog: TargetCatalog,
) {
    @GetMapping
    fun findCohorts(): List<CohortSummaryResponse> = queries.summaries().map { it.toResponse() }

    @GetMapping("/targets")
    fun listTargetOptions(): List<TargetOptionResponse> =
        queries.targets().map {
            TargetOptionResponse(it.target.id!!, it.target.system, it.target.kind, it.target.label, it.memberCount)
        }

    @GetMapping("/{id}")
    fun findCohortById(
        @PathVariable id: Long,
    ): CohortDetailResponse {
        val detail = queries.detail(id).toResponse()
        // Brevo is asked where each linked list is now, outside the read transaction.
        return detail.copy(
            mappings =
                detail.mappings.map { mapping ->
                    val externalId = mapping.externalId ?: return@map mapping
                    val place = catalog.placeOf(mapping.system, externalId)
                    mapping.copy(path = place.path, folderKnown = place.folderKnown)
                },
        )
    }

    @PostMapping("/{id}/targets/{targetId}/drift/push")
    fun pushDrift(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: PushDriftRequest,
    ): DriftResolvedResponse = DriftResolvedResponse(resolutions.push(id, targetId, body.userIds))

    @PostMapping("/{id}/targets/{targetId}/reconcile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reconcileTarget(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
    ) = resolutions.reconcile(id, targetId)

    @PostMapping("/users/{userId}/evaluate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun evaluateUser(
        @PathVariable userId: Long,
    ) = resolutions.evaluate(userId)

    @AdminOnly
    @PutMapping("/{id}/targets/{targetId}/enforced")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun enforceTarget(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: EnforceTargetRequest,
    ) = resolutions.enforce(id, targetId, body.enforced)

    @PostMapping("/{id}/targets/{targetId}/drift/remove")
    fun removeDrift(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: ExternalDriftRequest,
    ): DriftResolvedResponse = DriftResolvedResponse(resolutions.remove(id, targetId, body.externalUserIds))

    @PostMapping("/{id}/targets/{targetId}/drift/link/preview")
    fun proposeLinks(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: ExternalDriftRequest,
    ): List<LinkProposal> = resolutions.proposeLinks(id, targetId, body.externalUserIds)

    @PostMapping("/{id}/targets/{targetId}/drift/link")
    fun linkDrift(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: LinkDriftRequest,
    ): LinkOutcome = resolutions.link(id, targetId, body.links)

    @PostMapping("/{id}/targets/existing")
    fun linkExistingTarget(
        @PathVariable id: Long,
        @RequestBody @Valid body: LinkExistingTargetRequest,
    ): CohortTargetResponse = targeting.linkExisting(id, body.system, body.externalId).toResponse()

    @PostMapping("/{id}/targets/new")
    fun createTarget(
        @PathVariable id: Long,
        @RequestBody @Valid body: CreateTargetRequest,
    ): CohortTargetResponse = targeting.create(id, body.system, body.label, body.folderHint).toResponse()

    @AdminOnly
    @PutMapping("/{id}/targets/{targetId}")
    fun switchTarget(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: SwitchTargetRequest,
    ): CohortTargetResponse = targeting.switchTarget(id, targetId, body.externalId, body.deletePrevious, body.reconcileNow).toResponse()

    @PostMapping("/{id}/targets/{targetId}/inbound-reconcile/preview")
    fun previewInboundReconcile(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
    ): InboundReconcilePreview = inboundReconcile.preview(id, targetId)

    @PostMapping("/{id}/targets/{targetId}/inbound-reconcile/apply")
    fun applyInboundReconcile(
        @PathVariable id: Long,
        @PathVariable targetId: Long,
        @RequestBody @Valid body: InboundReconcileApplyRequest,
    ): InboundReconcileApplyResponse = inboundReconcile.apply(id, targetId, body)
}

@Schema(name = "TargetOption", description = "A target as a picker offers it.")
data class TargetOptionResponse(
    val id: Long,
    val system: String,
    val kind: TargetKind,
    val label: String,
    @param:Schema(description = "How many of our people the target holds")
    val memberCount: Int,
)

@Schema(name = "CohortSummary")
data class CohortSummaryResponse(
    val id: Long,
    val type: CohortType,
    val category: CohortCategory,
    val label: String,
    val memberCount: Int,
    val mappingCount: Int,
    @param:Schema(description = "Which definition in code decides who belongs here")
    val definitionKey: String?,
    @param:Schema(
        description = "The targets it has, one per system, and whether each is made there yet",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val targets: List<SummaryTarget>,
)

@Schema(name = "CohortDetail")
data class CohortDetailResponse(
    val id: Long,
    val type: CohortType,
    val category: CohortCategory,
    val label: String,
    val description: String?,
    val mappings: List<CohortTargetResponse>,
    @Schema(description = "Which definition in code decides who belongs here")
    val definitionKey: String?,
    @Schema(description = "True when no definition produces this cohort any more")
    val orphaned: Boolean,
    val members: List<CohortMemberResponse>,
    @param:Schema(description = "The latest drift resolutions across the cohort's targets, newest first")
    val resolutions: List<DriftResolutionResponse>,
)

@Schema(name = "CohortTarget")
data class CohortTargetResponse(
    /** The id of the underlying [net.blueshell.api.cohort.persistence.Target] row. */
    val targetId: Long,
    @field:Schema(description = "External system this mapping targets")
    val system: TargetSystem,
    val kind: TargetKind,
    val label: String,
    /** Native id on the external system; null until the cohort has been materialised. */
    val externalId: String?,
    @param:Schema(description = "When this cohort was last confirmed to agree with its target")
    val lastReconciledAt: Instant?,
    @param:Schema(
        description =
            "Where the target sits on its system, outside in: the system, then any " +
                "folder holding it, read from the system itself.",
    )
    val path: List<String>,
    @param:Schema(description = "False when the system could not say which folder the target is in")
    val folderKnown: Boolean = true,
    @param:Schema(description = "The target's recent reconciles, newest first; the first is its current drift")
    val runs: List<ReconcileRunResponse> = emptyList(),
    @param:Schema(description = "Whether each reconcile removes the target's theirs-only people")
    val enforced: Boolean = false,
)

@Schema(name = "ReconcileRun", description = "One reconcile of a target and the drift it found.")
data class ReconcileRunResponse(
    val startedAt: Instant,
    @param:Schema(description = "What queued it; null for a run queued before runs recorded it")
    val trigger: JobTrigger?,
    val inSync: Int,
    val oursOnly: Int,
    val theirsOnly: Int,
    @param:Schema(description = "Ours only with no account on the system, whom no push reaches")
    val unreachable: Int = 0,
)

@Schema(name = "CohortMember")
data class CohortMemberResponse(
    val targetMemberId: Long,
    @param:Schema(description = "Which system's ledger this row belongs to")
    val system: TargetSystem?,
    @param:Schema(description = "Whether this row is in step with the external system")
    val state: TargetMemberState?,
    @param:Schema(description = "Null for a row present externally with no local account")
    val userId: Long?,
    val userFullName: String?,
    val userEmail: String?,
    val isUserDeleted: Boolean,
    @param:Schema(description = "The row's identity in the external system, where it has one")
    val externalUserId: String?,
    @param:Schema(description = "What the external system calls this row")
    val externalLabel: String?,
    val joinedAt: Instant,
    @param:Schema(
        description = "No account on a system that cannot make one, so no push reaches them",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val unreachable: Boolean,
)

@Schema(name = "PushDrift")
data class PushDriftRequest(
    @field:NotEmpty val userIds: List<Long>,
)

@Schema(name = "EnforceTarget")
data class EnforceTargetRequest(
    @param:Schema(description = "Whether each reconcile removes the target's theirs-only people")
    val enforced: Boolean,
)

@Schema(name = "ExternalDrift")
data class ExternalDriftRequest(
    @field:NotEmpty val externalUserIds: List<String>,
)

@Schema(name = "LinkDrift")
data class LinkDriftRequest(
    @field:NotEmpty val links: List<LinkChoice>,
)

@Schema(name = "DriftResolved")
data class DriftResolvedResponse(
    @param:Schema(description = "How many people the action resolved; anyone no longer drifting is skipped")
    val resolved: Int,
)

@Schema(name = "DriftResolutionEntry")
data class DriftResolutionResponse(
    val targetId: Long,
    val system: TargetSystem,
    val action: DriftResolutionAction,
    val userId: Long?,
    val externalUserId: String?,
    @param:Schema(description = "The account's name, or what the target calls a person with none")
    val personName: String?,
    @param:Schema(description = "Who resolved it; null when the api did so on its own behalf")
    val resolvedByName: String?,
    val resolvedAt: Instant,
)

/** Map the cohort's target to an external target that already exists. */
@Schema(name = "LinkExistingTarget")
data class LinkExistingTargetRequest(
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val externalId: String,
)

/** Create a fresh external target and map the cohort's target to it. */
@Schema(name = "CreateTarget")
data class CreateTargetRequest(
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val label: String,
    val folderHint: String? = null,
)

/** Repoint an existing cohort mapping at a different external target. */
@Schema(name = "SwitchTarget")
data class SwitchTargetRequest(
    @field:NotBlank val externalId: String,
    val deletePrevious: Boolean = false,
    val reconcileNow: Boolean = false,
)

private fun CohortSummary.toResponse(): CohortSummaryResponse =
    CohortSummaryResponse(
        id = cohort.id!!,
        type = cohort.type,
        category = category,
        label = cohort.label,
        memberCount = memberCount,
        mappingCount = mappingCount,
        definitionKey = cohort.definitionKey,
        targets = targets,
    )

private fun CohortDetail.toResponse(): CohortDetailResponse =
    CohortDetailResponse(
        id = cohort.id!!,
        type = cohort.type,
        category = cohort.type.category(),
        label = cohort.label,
        description = cohort.description,
        mappings = mappings.map { it.toResponse() },
        definitionKey = definitionKey,
        orphaned = orphaned,
        members = members.map { it.toMemberResponse() },
        resolutions =
            resolutions.map {
                DriftResolutionResponse(
                    targetId = it.resolution.targetId,
                    system = it.system,
                    action = it.resolution.action,
                    userId = it.resolution.userId,
                    externalUserId = it.resolution.externalUserId,
                    personName = it.personName,
                    resolvedByName = it.resolvedByName,
                    resolvedAt = it.resolution.resolvedAt,
                )
            },
    )

private fun CohortTargetRow.toResponse(): CohortTargetResponse =
    CohortTargetResponse(
        targetId = target.id!!,
        system = TargetSystem.valueOf(target.system),
        kind = target.kind,
        label = target.label,
        externalId = externalId,
        lastReconciledAt = lastReconciledAt,
        path = path,
        runs = runs.map { ReconcileRunResponse(it.startedAt, it.trigger, it.inSync, it.oursOnly, it.theirsOnly, it.unreachable) },
        enforced = target.enforced,
    )

private fun TargetMemberRow.toMemberResponse(): CohortMemberResponse =
    CohortMemberResponse(
        targetMemberId = member.id!!,
        system = system,
        state = state,
        // The row's own user, or the account behind its external id once resolved: a stranger
        // whose external id is known belongs to somebody, and saying who is the point.
        userId = member.userId ?: resolvedUserId,
        userFullName = user?.fullName,
        userEmail = user?.email,
        isUserDeleted = isUserDeleted,
        externalUserId = member.externalUserId,
        externalLabel = member.label,
        joinedAt = member.createdAt,
        unreachable = unreachable,
    )
