package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import org.springframework.stereotype.Component

/**
 * Everybody on one team's line-up in the season fielded now, as read when the definitions were.
 * Which season that is follows the date, so the team cohorts are recomputed each night as well.
 */
class TeamPlayersDefinition(
    private val teamId: Long,
    override val label: String,
    private val players: Set<Long>,
) : CohortDefinition {
    override val key = "${CohortType.TEAM_PLAYERS}:$teamId"
    override val type = CohortType.TEAM_PLAYERS
    override val scope = teamId
    override val folder = CohortFolders.TEAMS

    override fun members(): Set<Long> = players

    override fun contains(userId: Long): Boolean = userId in players
}

@Component
class TeamPlayersProvider(
    private val rosters: TeamRosterService,
) : CohortDefinitionProvider {
    override val type = CohortType.TEAM_PLAYERS

    override fun definitions(): List<CohortDefinition> {
        val lineups = rosters.currentLineups()
        return rosters.teamNames().map { (id, name) -> TeamPlayersDefinition(id, name, lineups[id].orEmpty()) }
    }
}
