package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.CohortDetail
import net.blueshell.api.cohort.domain.CohortMemberRow
import net.blueshell.api.cohort.domain.CohortQueryService
import net.blueshell.api.cohort.domain.CohortSummary
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.domain.CohortRemediation
import net.blueshell.api.cohort.domain.CohortRepairResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Admin endpoints for cohort listings, per-cohort detail, and targeted
 * repair actions. Used today by the admin cohort dashboard and the
 * `CohortPicker.vue` component in the JobManager trigger modal.
 *
 * Full CRUD lands in a later PR; for now this is the picker- and
 * dashboard-side read plus operational repair.
 */
@RestController
@RequestMapping("/management/cohorts")
@Tag(name = "Cohorts", description = "Admin cohort listings + detail")
@PreAuthorize("hasAuthority('ADMIN')")
class CohortController(
    private val cohortQueries: CohortQueryService,
    private val remediation: CohortRemediation,
) {
    @GetMapping
    fun findCohorts(): List<CohortSummaryResponse> =
        cohortQueries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortById(@PathVariable id: Long): CohortDetailResponse =
        cohortQueries.detail(id).toResponse()

    @PostMapping("/{id}/repair-missing-adds")
    fun repairMissingAdds(@PathVariable id: Long): CohortRepairResponse =
        remediation.repairMissingAdds(id).toResponse()
}

@Schema(name = "CohortSummary")
data class CohortSummaryResponse(
    val id: Long,
    val system: String,
    val kind: CohortKind,
    val label: String,
    /** Optional grouping name shown as a section header in the admin UI; null means "ungrouped". */
    val folder: String?,
    val memberCount: Int,
    /** Native id on the external system; null until the cohort is materialised externally. */
    val externalId: String?,
)

@Schema(name = "CohortDetail")
data class CohortDetailResponse(
    val id: Long,
    val system: String,
    val kind: CohortKind,
    val label: String,
    val folder: String?,
    val memberCount: Int,
    val externalId: String?,
    val members: List<CohortMemberRowResponse>,
    @Schema(description = "Which definition in code decides who belongs to this cohort")
    val definitionKey: String?,
)

@Schema(name = "CohortMemberRow")
data class CohortMemberRowResponse(
    val cohortMemberId: Long,
    val userId: Long,
    /** Null when the user has been hard-deleted but the cohort_member row still exists. */
    val userFullName: String?,
    val userEmail: String?,
    /**
     * True when the user is soft-deleted: the cohort_member row was
     * preserved for historical stats but the user is no longer active.
     * The admin UI renders these rows muted with a "Deleted" tag.
     */
    val isUserDeleted: Boolean,
    val joinedAt: Instant,
)

@Schema(name = "CohortRepair")
data class CohortRepairResponse(val cohortId: Long, val enqueuedAdds: Int)

private fun CohortSummary.toResponse(): CohortSummaryResponse =
    CohortSummaryResponse(
        id = cohort.id!!,
        system = cohort.system,
        kind = cohort.kind,
        label = cohort.label,
        folder = cohort.folder,
        memberCount = memberCount,
        externalId = externalId,
    )

private fun CohortDetail.toResponse(): CohortDetailResponse =
    CohortDetailResponse(
        id = cohort.id!!,
        system = cohort.system,
        kind = cohort.kind,
        label = cohort.label,
        folder = cohort.folder,
        memberCount = members.size,
        externalId = externalId,
        members = members.map { it.toResponse() },
        definitionKey = definitionKey,
    )

private fun CohortMemberRow.toResponse(): CohortMemberRowResponse =
    CohortMemberRowResponse(
        cohortMemberId = member.id!!,
        userId = member.userId!!,
        userFullName = user?.fullName,
        userEmail = user?.email,
        isUserDeleted = isUserDeleted,
        joinedAt = member.createdAt,
    )

private fun CohortRepairResult.toResponse(): CohortRepairResponse =
    CohortRepairResponse(cohortId = cohortId, enqueuedAdds = enqueuedAdds)
