package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.persistence.TeamSeason
import net.blueshell.api.domain.esports.persistence.repository.TeamRosterEntryRepository
import net.blueshell.api.domain.esports.persistence.repository.TeamSeasonRepository
import net.blueshell.api.shared.enums.Game
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Which teams the association fielded in which season.
 *
 * Fielding a team and naming its players are two different decisions taken at two different
 * times, so this answers the first without waiting for the second.
 */
@Service
class TeamSeasonService(
    private val fielded: TeamSeasonRepository,
    private val entries: TeamRosterEntryRepository,
    private val teams: TeamService,
    private val seasons: SeasonService,
) {
    @Transactional(readOnly = true)
    fun findByGameAndSeason(game: Game, seasonId: Long): List<TeamSeason> =
        fielded.findAllByGameAndSeason(game, seasonId)

    @Transactional(readOnly = true)
    fun findSeasonIdsFielded(game: Game): List<Long> = fielded.findSeasonIdsFielded(game)

    /** The seasons a team was fielded in, newest first. */
    @Transactional(readOnly = true)
    fun seasonsOf(teamId: Long): List<TeamSeason> = fielded.findAllByTeamId(teamId)

    /**
     * How much a season holds, so a removal can say what goes with it before it happens.
     *
     * Counted rather than listed: what the reader needs before deciding is the size of what
     * they are about to hide, not its contents.
     */
    @Transactional(readOnly = true)
    fun contentsOf(seasonId: Long): Pair<Long, Long> =
        fielded.countBySeasonId(seasonId) to entries.countBySeasonId(seasonId)

    @Transactional(readOnly = true)
    fun isFielded(teamId: Long, seasonId: Long): Boolean =
        fielded.findByTeamIdAndSeasonId(teamId, seasonId) != null

    /**
     * Records that a team is fielded in a season. Saying so twice says the same thing, so a
     * repeat is the existing link rather than a second one or an error.
     */
    @Transactional
    fun field(teamId: Long, seasonId: Long): TeamSeason =
        fielded.findByTeamIdAndSeasonId(teamId, seasonId)
            ?: fielded.save(TeamSeason(team = teams.findById(teamId), season = seasons.findById(seasonId)))

    /**
     * Stops a team being fielded in a season. The team, and its other seasons, are untouched:
     * a team dropped from one season still played the others.
     */
    @Transactional
    fun unfield(teamId: Long, seasonId: Long) {
        fielded.findByTeamIdAndSeasonId(teamId, seasonId)?.let { fielded.delete(it) }
    }
}
