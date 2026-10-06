package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import org.springframework.stereotype.Component

/**
 * Everybody on one team's line-up in the season fielded now. A season ending empties the definition
 * but takes nobody off the team's role: drift is reported, never corrected, so a team on hiatus keeps
 * its players until the board removes them.
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

/** Everybody on any team's line-up in the season fielded now: the esports team members, server-wide. */
class CurrentTeamPlayersDefinition(
    private val rosters: TeamRosterService,
) : CohortDefinition {
    override val key = CohortType.CURRENT_TEAM_PLAYERS.name
    override val type = CohortType.CURRENT_TEAM_PLAYERS
    override val scope = null
    override val label = "Esports team members"
    override val folder = CohortFolders.TEAMS

    override fun members(): Set<Long> = rosters.teamNames().keys.flatMapTo(mutableSetOf()) { rosters.currentPlayersOf(it) }

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class CurrentTeamPlayersProvider(
    private val rosters: TeamRosterService,
) : CohortDefinitionProvider {
    override val type = CohortType.CURRENT_TEAM_PLAYERS

    override fun definitions(): List<CohortDefinition> = listOf(CurrentTeamPlayersDefinition(rosters))
}
