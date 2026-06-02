package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.CohortQueryService
import net.blueshell.api.platform.integration.cohort.application.CohortSummary
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only admin endpoint for cohort listings. Used today by the
 * `CohortPicker.vue` component in the JobManager trigger modal and
 * any future admin UI that needs to select a cohort by human label.
 *
 * Full CRUD lands in a later PR (the planned admin "cohort manager"
 * page); for now this is just the picker-side read.
 */
@RestController
@RequestMapping("/management/cohorts")
@Tag(name = "Cohorts", description = "Read-only cohort listings for admin UI pickers")
@PreAuthorize("hasAuthority('ADMIN')")
class CohortController(
    private val cohortQueries: CohortQueryService,
) {
    @GetMapping
    fun findCohorts(): List<CohortSummaryResponse> =
        cohortQueries.summaries().map { it.toResponse() }
}

@Schema(name = "CohortSummary")
data class CohortSummaryResponse(
    val id: Long,
    val system: String,
    val kind: CohortKind,
    val label: String,
    val memberCount: Int,
)

private fun CohortSummary.toResponse(): CohortSummaryResponse =
    CohortSummaryResponse(
        id = cohort.id!!,
        system = cohort.system,
        kind = cohort.kind,
        label = cohort.label,
        memberCount = memberCount,
    )
