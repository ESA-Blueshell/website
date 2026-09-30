package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.cohort.domain.CohortMappingRow
import net.blueshell.api.cohort.domain.CohortMemberRow
import net.blueshell.api.cohort.domain.CohortSubjectDetail
import net.blueshell.api.cohort.domain.CohortSubjectQueryService
import net.blueshell.api.cohort.domain.CohortSubjectSummary
import net.blueshell.api.cohort.domain.CohortTargeting
import net.blueshell.api.cohort.domain.DriftResolutions
import net.blueshell.api.cohort.domain.InboundReconcile
import net.blueshell.api.cohort.domain.InboundReconcileApplyRequest
import net.blueshell.api.cohort.domain.InboundReconcileApplyResponse
import net.blueshell.api.cohort.domain.InboundReconcilePreview
import net.blueshell.api.cohort.domain.LinkChoice
import net.blueshell.api.cohort.domain.LinkOutcome
import net.blueshell.api.cohort.domain.LinkProposal
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.security.AdminOnly
import net.blueshell.api.shared.enums.CohortMemberState
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

/**
 * Admin endpoints for the new Subjects dashboard. A Subject is the
 * logical thing the engine syncs (Web Cmte, Members 2025-2026,
 * Newsletter Subscribers); each subject has one or more per-system
 * mappings exposed nested under it.
 *
 * The old `/management/cohorts` endpoints stay in place for the
 * picker components and the existing per-cohort drill-down until
 * the engine itself is refactored onto subjects (follow-up PR).
 */
@RestController
@RequestMapping("/management/cohort-subjects")
@Tag(name = "Cohort Subjects", description = "Admin: logical subjects + their per-system mappings")
@AdminOnly
class CohortSubjectController(
    private val queries: CohortSubjectQueryService,
    private val resolutions: DriftResolutions,
    private val targeting: CohortTargeting,
    private val inboundReconcile: InboundReconcile,
    private val catalog: TargetCatalog,
) {
    @GetMapping
    fun findCohortSubjects(): List<CohortSubjectSummaryResponse> = queries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortSubjectById(
        @PathVariable id: Long,
    ): CohortSubjectDetailResponse {
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

    @PostMapping("/{id}/targets/{cohortId}/drift/push")
    fun pushDrift(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: PushDriftRequest,
    ): DriftResolvedResponse = DriftResolvedResponse(resolutions.push(id, cohortId, body.userIds))

    @PutMapping("/{id}/targets/{cohortId}/enforced")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun enforceTarget(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: EnforceTargetRequest,
    ) = resolutions.enforce(id, cohortId, body.enforced)

    @PostMapping("/{id}/targets/{cohortId}/drift/remove")
    fun removeDrift(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: ExternalDriftRequest,
    ): DriftResolvedResponse = DriftResolvedResponse(resolutions.remove(id, cohortId, body.externalUserIds))

    @PostMapping("/{id}/targets/{cohortId}/drift/link/preview")
    fun proposeLinks(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: ExternalDriftRequest,
    ): List<LinkProposal> = resolutions.proposeLinks(id, cohortId, body.externalUserIds)

    @PostMapping("/{id}/targets/{cohortId}/drift/link")
    fun linkDrift(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: LinkDriftRequest,
    ): LinkOutcome = resolutions.link(id, cohortId, body.links)

    @PostMapping("/{id}/targets/existing")
    fun linkExistingTarget(
        @PathVariable id: Long,
        @RequestBody @Valid body: LinkExistingTargetRequest,
    ): CohortMappingResponse = targeting.linkExisting(id, body.system, body.externalId).toResponse()

    @PostMapping("/{id}/targets/new")
    fun createTarget(
        @PathVariable id: Long,
        @RequestBody @Valid body: CreateTargetRequest,
    ): CohortMappingResponse = targeting.create(id, body.system, body.label, body.folderHint).toResponse()

    @PutMapping("/{id}/targets/{cohortId}")
    fun switchTarget(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: SwitchTargetRequest,
    ): CohortMappingResponse = targeting.switchTarget(id, cohortId, body.externalId, body.deletePrevious, body.reconcileNow).toResponse()

    @PostMapping("/{id}/targets/{cohortId}/inbound-reconcile/preview")
    fun previewInboundReconcile(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
    ): InboundReconcilePreview = inboundReconcile.preview(id, cohortId)

    @PostMapping("/{id}/targets/{cohortId}/inbound-reconcile/apply")
    fun applyInboundReconcile(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: InboundReconcileApplyRequest,
    ): InboundReconcileApplyResponse = inboundReconcile.apply(id, cohortId, body)
}

@Schema(name = "CohortSubjectSummary")
data class CohortSubjectSummaryResponse(
    val id: Long,
    val type: CohortSubjectType,
    val category: CohortSubjectCategory,
    val label: String,
    val memberCount: Int,
    val mappingCount: Int,
)

@Schema(name = "CohortSubjectDetail")
data class CohortSubjectDetailResponse(
    val id: Long,
    val type: CohortSubjectType,
    val category: CohortSubjectCategory,
    val label: String,
    val description: String?,
    val mappings: List<CohortMappingResponse>,
    @Schema(description = "Which definition in code decides who belongs here")
    val definitionKey: String?,
    @Schema(description = "True when no definition produces this cohort any more")
    val orphaned: Boolean,
    val members: List<CohortSubjectMemberResponse>,
    @param:Schema(description = "The latest drift resolutions across the subject's targets, newest first")
    val resolutions: List<DriftResolutionResponse>,
)

@Schema(name = "CohortMapping")
data class CohortMappingResponse(
    /** The id of the underlying [net.blueshell.api.cohort.persistence.Cohort] row. */
    val cohortId: Long,
    @field:Schema(description = "External system this mapping targets")
    val system: TargetSystem,
    val kind: CohortKind,
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
)

@Schema(name = "CohortSubjectMember")
data class CohortSubjectMemberResponse(
    val cohortMemberId: Long,
    @param:Schema(description = "Which system's ledger this row belongs to")
    val system: TargetSystem?,
    @param:Schema(description = "Whether this row is in step with the external system")
    val state: CohortMemberState?,
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
    val cohortId: Long,
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

/** Map the subject's per-system cohort to an external target that already exists. */
@Schema(name = "LinkExistingTarget")
data class LinkExistingTargetRequest(
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val externalId: String,
)

/** Create a fresh external target and map the subject's per-system cohort to it. */
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

private fun CohortSubjectSummary.toResponse(): CohortSubjectSummaryResponse =
    CohortSubjectSummaryResponse(
        id = subject.id!!,
        type = subject.type,
        category = category,
        label = subject.label,
        memberCount = memberCount,
        mappingCount = mappingCount,
    )

private fun CohortSubjectDetail.toResponse(): CohortSubjectDetailResponse =
    CohortSubjectDetailResponse(
        id = subject.id!!,
        type = subject.type,
        category = subject.type.category(),
        label = subject.label,
        description = subject.description,
        mappings = mappings.map { it.toResponse() },
        definitionKey = definitionKey,
        orphaned = orphaned,
        members = members.map { it.toMemberResponse() },
        resolutions =
            resolutions.map {
                DriftResolutionResponse(
                    cohortId = it.resolution.cohortId,
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

private fun CohortMappingRow.toResponse(): CohortMappingResponse =
    CohortMappingResponse(
        cohortId = cohort.id!!,
        system = TargetSystem.valueOf(cohort.system),
        kind = cohort.kind,
        label = cohort.label,
        externalId = externalId,
        lastReconciledAt = lastReconciledAt,
        path = path,
        runs = runs.map { ReconcileRunResponse(it.startedAt, it.trigger, it.inSync, it.oursOnly, it.theirsOnly) },
        enforced = cohort.enforced,
    )

private fun CohortMemberRow.toMemberResponse(): CohortSubjectMemberResponse =
    CohortSubjectMemberResponse(
        cohortMemberId = member.id!!,
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
    )
