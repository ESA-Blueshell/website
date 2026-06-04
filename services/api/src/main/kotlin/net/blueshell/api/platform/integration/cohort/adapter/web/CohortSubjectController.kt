package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.CohortMappingRow
import net.blueshell.api.platform.integration.cohort.application.CohortMemberRow
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectDetail
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectQueryService
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectSummary
import net.blueshell.api.platform.integration.cohort.application.DriftReport
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
@PreAuthorize("hasAuthority('ADMIN')")
class CohortSubjectController(
    private val queries: CohortSubjectQueryService,
    private val drift: CohortDrift,
    private val remediation: CohortRemediation,
    private val targeting: CohortTargeting,
) {
    @GetMapping
    fun findCohortSubjects(): List<CohortSubjectSummaryResponse> =
        queries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortSubjectById(@PathVariable id: Long): CohortSubjectDetailResponse =
        queries.detail(id).toResponse()

    @GetMapping("/{id}/drift")
    fun getDrift(
        @PathVariable id: Long,
        @RequestParam system: TargetSystem,
    ): DriftReport =
        drift.compute(id, system)

    @PostMapping("/{id}/drift/link-user")
    fun linkUser(
        @PathVariable id: Long,
        @RequestBody @Valid body: LinkUserRequest,
    ): LinkedUserResponse {
        val mapping = remediation.linkUser(
            subjectId = id,
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
    ): CohortMappingResponse =
        targeting.linkExisting(id, body.system, body.externalId).toResponse()

    @PostMapping("/{id}/targets/new")
    fun createTarget(
        @PathVariable id: Long,
        @RequestBody @Valid body: CreateTargetRequest,
    ): CohortMappingResponse =
        targeting.create(id, body.system, body.label, body.folderHint).toResponse()

    @PutMapping("/{id}/targets/{cohortId}")
    fun switchTarget(
        @PathVariable id: Long,
        @PathVariable cohortId: Long,
        @RequestBody @Valid body: SwitchTargetRequest,
    ): CohortMappingResponse =
        targeting.switchTarget(id, cohortId, body.externalId, body.deletePrevious, body.reconcileNow).toResponse()
}

// ── Request / response DTOs ──────────────────────────────────────────────────

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
    val rules: List<CohortSubjectRuleResponse>,
    val members: List<CohortSubjectMemberResponse>,
)

@Schema(name = "CohortMapping")
data class CohortMappingResponse(
    /** The id of the underlying [net.blueshell.api.platform.integration.cohort.persistence.Cohort] row. */
    val cohortId: Long,
    @field:Schema(description = "External system this mapping targets")
    val system: TargetSystem,
    val kind: CohortKind,
    val label: String,
    /** Native id on the external system; null until the cohort has been materialised. */
    val externalId: String?,
)

@Schema(name = "CohortSubjectRule")
data class CohortSubjectRuleResponse(
    val id: Long,
    val factKind: CohortFactKind,
    val factKey: String,
    val enabled: Boolean,
)

@Schema(name = "CohortSubjectMember")
data class CohortSubjectMemberResponse(
    val cohortMemberId: Long,
    val userId: Long,
    val userFullName: String?,
    val userEmail: String?,
    val isUserDeleted: Boolean,
    val joinedAt: Instant,
)

data class LinkUserRequest(
    @field:NotNull val userId: Long,
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val externalUserId: String,
)

@Schema(name = "LinkedUser")
data class LinkedUserResponse(val userId: Long, val system: TargetSystem, val externalUserId: String)

/** Map the subject's per-system cohort to an external target that already exists. */
data class LinkExistingTargetRequest(
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val externalId: String,
)

/** Create a fresh external target and map the subject's per-system cohort to it. */
data class CreateTargetRequest(
    @field:NotNull val system: TargetSystem,
    @field:NotBlank val label: String,
    val folderHint: String? = null,
)

/** Repoint an existing cohort mapping at a different external target. */
data class SwitchTargetRequest(
    @field:NotBlank val externalId: String,
    val deletePrevious: Boolean = false,
    val reconcileNow: Boolean = false,
)

// ── Extension mappings ───────────────────────────────────────────────────────

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
        rules = rules.map {
            CohortSubjectRuleResponse(
                id = it.id!!,
                factKind = it.factKind,
                factKey = it.factKey,
                enabled = it.enabled,
            )
        },
        members = members.map { it.toMemberResponse() },
    )

private fun CohortMappingRow.toResponse(): CohortMappingResponse =
    CohortMappingResponse(
        cohortId = cohort.id!!,
        system = TargetSystem.valueOf(cohort.system),
        kind = cohort.kind,
        label = cohort.label,
        externalId = externalId,
    )

private fun CohortMemberRow.toMemberResponse(): CohortSubjectMemberResponse =
    CohortSubjectMemberResponse(
        cohortMemberId = member.id!!,
        userId = member.userId!!,
        userFullName = user?.fullName,
        userEmail = user?.email,
        isUserDeleted = isUserDeleted,
        joinedAt = member.createdAt,
    )
