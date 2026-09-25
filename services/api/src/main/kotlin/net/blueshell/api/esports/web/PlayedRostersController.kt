package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.esports.api.TeamRosterService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/** The seasons and teams somebody played for, as the rosters they are linked on say. */
@RestController
@Tag(name = "Game accounts", description = "Per-member, per-game handles")
class PlayedRostersController(
    private val rosters: TeamRosterService,
) {
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    @GetMapping("/users/{userId}/rosters")
    fun findPlayedRosters(
        @PathVariable userId: Long,
    ): List<PlayedRosterResponse> = rosters.playedBy(userId).map { it.asPlayedResponse() }
}
