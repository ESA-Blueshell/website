package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.PeriodStanding
import net.blueshell.api.contribution.domain.PeriodStandings
import net.blueshell.api.security.BoardOnly
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "ContributionPeriods")
class PeriodStandingController(
    private val standings: PeriodStandings,
) {
    @GetMapping("/contributionPeriods/current/standing")
    @BoardOnly
    fun findCurrentPeriodStanding(): ResponseEntity<PeriodStanding> =
        standings.current()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
}
