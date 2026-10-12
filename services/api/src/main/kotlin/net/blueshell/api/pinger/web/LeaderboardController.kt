package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.pinger.api.LeaderboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The public SNTPings leaderboard.
 *
 * Open to anyone; every contributor appears, shown by their public identity, and SiteCie is a
 * labelled house line outside the ranking.
 */
@RestController
@RequestMapping("/pinger/leaderboard")
@Tag(name = "Pinger leaderboard", description = "The public SNTPings leaderboard")
class LeaderboardController(
    private val leaderboard: LeaderboardService,
) {
    @PermitAll
    @GetMapping
    fun board(): LeaderboardResponse = LeaderboardResponse.from(leaderboard.snapshot())
}
