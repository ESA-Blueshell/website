package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.CohortMappingRow
import net.blueshell.api.platform.integration.cohort.application.CohortMemberRow
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectDetail
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectQueryService
import net.blueshell.api.platform.integration.cohort.application.CohortSubjectSummary
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
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
) {
    @GetMapping
    fun findCohortSubjects(): List<CohortSubjectSummaryResponse> =
        queries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortSubjectById(@PathVariable id: Long): CohortSubjectDetailResponse =
        queries.detail(id).toResponse()
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
        userId = member.userId,
        userFullName = user?.fullName,
        userEmail = user?.email,
        isUserDeleted = isUserDeleted,
        joinedAt = member.createdAt,
    )
