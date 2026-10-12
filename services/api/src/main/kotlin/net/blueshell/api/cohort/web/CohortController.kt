package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.cohort.domain.CohortDetail
import net.blueshell.api.cohort.domain.CohortQueryService
import net.blueshell.api.cohort.domain.CohortRemediation
import net.blueshell.api.cohort.domain.CohortSummary
import net.blueshell.api.cohort.domain.CohortTargetRow
import net.blueshell.api.cohort.domain.CohortTargeting
import net.blueshell.api.cohort.domain.InboundReconcile
import net.blueshell.api.cohort.domain.InboundReconcileApplyRequest
import net.blueshell.api.cohort.domain.InboundReconcileApplyResponse
import net.blueshell.api.cohort.domain.InboundReconcilePreview
import net.blueshell.api.cohort.domain.TargetMemberRow
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.security.AdminOnly
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The cohorts, each with the targets that mirror it on the external systems, and their drift. */
@RestController
@RequestMapping("/management/cohorts")
@Tag(name = "Cohorts", description = "Admin: cohorts and the targets that mirror them")
@AdminOnly
class CohortController(
    private val queries: CohortQueryService,
    private val remediation: CohortRemediation,
    private val targeting: CohortTargeting,
    private val inboundReconcile: InboundReconcile,
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
    ): CohortDetailResponse = queries.detail(id).toResponse()

    @PostMapping("/{id}/drift/link-user")
    fun linkUser(
        @PathVariable id: Long,
        @RequestBody @Valid body: LinkUserRequest,
    ): LinkedUserResponse {
        val mapping =
            remediation.linkUser(
                cohortId = id,
                userId = body.userId,
                system = body.system,
                externalUserId = body.externalUserId,
            )
        return LinkedUserResponse(
            userId = mapping.aggregateId,
            system = TargetSystem.valueOf(mapping.system),
            externalUserId = mapping.externalId ?: body.externalUserId,
        )
    }

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
                "folder holding it. Read from what was recorded when the target was linked or " +
                "moved, so a page costs no call to the system.",
    )
    val path: List<String>,
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
)

@Schema(name = "LinkUser")
data class LinkUserRequest(
    @field:NotNull val userId: Long,
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val externalUserId: String,
)

@Schema(name = "LinkedUser")
data class LinkedUserResponse(
    val userId: Long,
    val system: TargetSystem,
    val externalUserId: String,
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
    )
