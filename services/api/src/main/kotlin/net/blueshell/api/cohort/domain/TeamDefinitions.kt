package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import org.springframework.stereotype.Component

/**
 * Everybody on one team's line-up in the season fielded now. Nothing announces a season ending, so
 * its players keep the team's role until something about them changes or the board removes it.
 */
class TeamPlayersDefinition(
    private val rosters: TeamRosterService,
    private val teamId: Long,
    override val label: String,
) : CohortDefinition {
    override val key = "${CohortType.TEAM_PLAYERS}:$teamId"
    override val type = CohortType.TEAM_PLAYERS
    override val scope = teamId
    override val folder = CohortFolders.TEAMS

    override fun members(): Set<Long> = rosters.currentPlayersOf(teamId)

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class TeamPlayersProvider(
    private val rosters: TeamRosterService,
) : CohortDefinitionProvider {
    override val type = CohortType.TEAM_PLAYERS

    override fun definitions(): List<CohortDefinition> = rosters.teamNames().map { (id, name) -> TeamPlayersDefinition(rosters, id, name) }
}
