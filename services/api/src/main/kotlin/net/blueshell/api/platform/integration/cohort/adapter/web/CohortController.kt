package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.CohortDetail
import net.blueshell.api.platform.integration.cohort.application.CohortMemberRow
import net.blueshell.api.platform.integration.cohort.application.CohortQueryService
import net.blueshell.api.platform.integration.cohort.application.CohortSummary
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Read-only admin endpoints for cohort listings + the per-cohort
 * detail view (members + rules). Used today by the admin cohort
 * dashboard and the `CohortPicker.vue` component in the JobManager
 * trigger modal.
 *
 * Full CRUD lands in a later PR; for now this is the picker- and
 * dashboard-side read.
 */
@RestController
@RequestMapping("/management/cohorts")
@Tag(name = "Cohorts", description = "Admin cohort listings + detail")
@PreAuthorize("hasAuthority('ADMIN')")
class CohortController(
    private val cohortQueries: CohortQueryService,
) {
    @GetMapping
    fun findCohorts(): List<CohortSummaryResponse> =
        cohortQueries.summaries().map { it.toResponse() }

    @GetMapping("/{id}")
    fun findCohortById(@PathVariable id: Long): CohortDetailResponse =
        cohortQueries.detail(id).toResponse()
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
    val rules: List<CohortRuleResponse>,
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

@Schema(name = "CohortRule")
data class CohortRuleResponse(
    val id: Long,
    val factKind: CohortFactKind,
    val factKey: String,
    val enabled: Boolean,
)

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
        rules = rules.map {
            CohortRuleResponse(
                id = it.id!!,
                factKind = it.factKind,
                factKey = it.factKey,
                enabled = it.enabled,
            )
        },
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
