package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.PeriodContributions
import net.blueshell.api.contribution.domain.PeriodContributionsView
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "ContributionPeriods")
class PeriodContributionsController(
    private val contributions: PeriodContributions,
) {
    @GetMapping("/contributionPeriods/{periodId}/members")
    @BoardOnly
    fun findPeriodContributions(
        @PathVariable periodId: Long,
    ): PeriodContributionsView = contributions.of(periodId)
}
