package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.CohortMappingRow
import net.blueshell.api.platform.integration.cohort.application.CohortMemberRow
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectDetail
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectQueryService
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectSummary
import net.blueshell.api.platform.integration.cohort.application.DriftReport
import net.blueshell.api.platform.integration.cohort.application.ExtraRow
import net.blueshell.api.platform.integration.cohort.application.MissingRow
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
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
    private val dispatcher: TrackedJobDispatcher,
    private val registry: CohortPortRegistry,
) {
    @GetMapping
    fun findCohortSubjects(): List<CohortSubjectSummaryResponse> =
        queries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortSubjectById(@PathVariable id: Long): CohortSubjectDetailResponse =
        queries.detail(id).toResponse()

    @GetMapping("/systems")
    fun listSystems(): List<TargetSystem> =
        registry.systems().sortedBy { it.name }

    @GetMapping("/{id}/drift")
    fun getDrift(
        @PathVariable id: Long,
        @RequestParam system: TargetSystem,
    ): DriftResponse =
        drift.compute(id, system).toResponse()

    @PostMapping("/{id}/drift/remove-external")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun removeExternal(
        @PathVariable id: Long,
        @RequestBody body: RemoveExternalRequest,
    ): EnqueueResponse {
        val execution = dispatcher.enqueue(
            CohortJobs.RemoveExternalMember,
            CohortJobs.RemoveExternalMemberPayload(
                cohortId = body.cohortId,
                externalUserId = body.externalUserId,
            ),
        )
        return EnqueueResponse(jobId = execution?.id)
    }

    @PostMapping("/{id}/drift/reconcile")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun reconcile(
        @PathVariable id: Long,
        @RequestParam system: TargetSystem,
    ): EnqueueResponse {
        val cohort = queries.findCohortBySubjectAndSystem(id, system)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No $system mapping for subject $id")
        val execution = dispatcher.enqueue(
            CohortJobs.ReconcileList,
            CohortJobs.ReconcileListPayload(cohortId = cohort.id!!),
        )
        return EnqueueResponse(jobId = execution?.id)
    }

    @PostMapping("/{id}/drift/link-user")
    fun linkUser(
        @PathVariable id: Long,
        @RequestBody body: LinkUserRequest,
    ): LinkedUserResponse {
        val mapping = remediation.linkUser(id, body.userId, body.system, body.externalUserId)
        return LinkedUserResponse(
            userId = mapping.aggregateId,
            system = TargetSystem.valueOf(mapping.system),
            externalUserId = mapping.externalId ?: body.externalUserId,
        )
    }
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
    val system: String,
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

@Schema(name = "DriftReport")
data class DriftResponse(
    val cohortId: Long,
    val system: TargetSystem,
    val externalCohortId: String?,
    val extras: List<ExtraRowResponse>,
    val missing: List<MissingRowResponse>,
    val lastReconciledAt: Instant?,
)

@Schema(name = "ExtraRow")
data class ExtraRowResponse(
    val externalUserId: String,
    val label: String?,
    val kind: String, // "KNOWN_LOCAL_USER" | "UNKNOWN_EXTERNAL"
    val userId: Long?,
    val fullName: String?,
    val email: String?,
    val softDeleted: Boolean?,
)

@Schema(name = "MissingRow")
data class MissingRowResponse(
    val userId: Long,
    val hasExternalMapping: Boolean,
)

data class RemoveExternalRequest(val cohortId: Long, val externalUserId: String)

data class LinkUserRequest(val userId: Long, val system: TargetSystem, val externalUserId: String)

@Schema(name = "EnqueueResponse")
data class EnqueueResponse(
    /** Job execution id; null if dedup collapsed the enqueue. */
    val jobId: Long?,
)

@Schema(name = "LinkedUser")
data class LinkedUserResponse(val userId: Long, val system: TargetSystem, val externalUserId: String)

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
        system = cohort.system,
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

private fun DriftReport.toResponse(): DriftResponse =
    DriftResponse(
        cohortId = cohortId,
        system = system,
        externalCohortId = externalCohortId,
        extras = extras.map { it.toResponse() },
        missing = missing.map { MissingRowResponse(it.userId, it.hasExternalMapping) },
        lastReconciledAt = lastReconciledAt,
    )

private fun ExtraRow.toResponse(): ExtraRowResponse = when (this) {
    is ExtraRow.KnownLocalUser -> ExtraRowResponse(
        externalUserId = externalUserId,
        label = label,
        kind = "KNOWN_LOCAL_USER",
        userId = userId,
        fullName = fullName,
        email = email,
        softDeleted = softDeleted,
    )
    is ExtraRow.UnknownExternal -> ExtraRowResponse(
        externalUserId = externalUserId,
        label = label,
        kind = "UNKNOWN_EXTERNAL",
        userId = null,
        fullName = null,
        email = null,
        softDeleted = null,
    )
}
