package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Which teams the association fielded in which game, in which season.
 *
 * Fielding a team and naming its players are decided at different times, so this answers the
 * first without waiting for the second. Every question names a game as well as a team: the same
 * team can be fielded in two games in one season, and each fielding has a line-up and art of its
 * own. A code naming no game is refused before anything is written.
 */
@Service
class TeamSeasonService(
    private val fielded: TeamSeasonRepository,
    private val entries: TeamRosterEntryRepository,
    private val teams: TeamService,
    private val seasons: SeasonService,
    private val games: GameService,
    private val pictures: StoredPictures,
) {
    @Transactional(readOnly = true)
    fun findByGameAndSeason(game: String, seasonId: Long): List<TeamSeason> =
        fielded.findAllByGameAndSeason(game, seasonId)

    @Transactional(readOnly = true)
    fun findSeasonIdsFielded(game: String): List<Long> = fielded.findSeasonIdsFielded(game)

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
    fun isFielded(teamId: Long, game: String, seasonId: Long): Boolean =
        fielded.findByTeamIdAndGameAndSeasonId(teamId, game, seasonId) != null

    /**
     * Records that a team is fielded in a season. Saying so twice says the same thing, so a
     * repeat is the existing link rather than a second one or an error.
     *
     * A team fielded again in a season it was dropped from revives its old fielding rather than
     * writing a second: the line-up hangs off the fielding, so a second row would strand last
     * time's line-up on the dropped one, reachable by nothing.
     */
    @Transactional
    fun field(teamId: Long, game: String, seasonId: Long): TeamSeason {
        fielded.findByTeamIdAndGameAndSeasonId(teamId, game, seasonId)?.let { return it }
        fielded.findDroppedId(teamId, game, seasonId)?.let { dropped ->
            fielded.revive(dropped)
            fielded.findByTeamIdAndGameAndSeasonId(teamId, game, seasonId)?.let { return it }
        }
        return fielded.save(
            TeamSeason(
                team = teams.findById(teamId),
                game = games.requireGame(game).code,
                season = seasons.findById(seasonId),
                // The art a team was last drawn with in this game comes across, so a season is
                // only asked for a picture when the picture should change. A team playing a game
                // for the first time starts without one, which is what a new team looks like.
                banner = fielded.findPreviousInGame(teamId, game, seasonId).firstOrNull()?.banner,
            ),
        )
    }

    /**
     * The games fielded in the season we are in, falling back to the one before only while that
     * season has nothing fielded yet — a season is built a game at a time.
     */
    @Transactional(readOnly = true)
    fun currentlyPlayed(on: LocalDate = LocalDate.now()): Set<String> {
        val seasonId = fieldedSeasonId(on) ?: return emptySet()
        return fielded.gamesFieldedIn(listOf(seasonId)).toSet()
    }

    /** How many teams stand in the season [currentlyPlayed] reads, which is the same season. */
    @Transactional(readOnly = true)
    fun teamsFieldedNow(on: LocalDate = LocalDate.now()): Long {
        val seasonId = fieldedSeasonId(on) ?: return 0
        return fielded.countBySeasonId(seasonId)
    }

    /**
     * The season a reader means by "now": the one we are in, or the one before it while this
     * one has nothing fielded yet — a season is built a game at a time.
     */
    private fun fieldedSeasonId(on: LocalDate): Long? {
        val ordered = seasons.findAll()
        // No season covers the date — a gap, or every season already over.
        val current = seasons.findCurrent(on) ?: ordered.firstOrNull { !it.startDate.isAfter(on) }
            ?: return null
        val currentId = current.id ?: return null
        if (fielded.gamesFieldedIn(listOf(currentId)).isNotEmpty()) return currentId
        return ordered.firstOrNull { it.startDate.isBefore(current.startDate) }?.id
    }

    /** The seasons that had a team fielded in them, whichever game it played. */
    @Transactional(readOnly = true)
    fun seasonsWithTeams(): Set<Long> = fielded.seasonIdsWithTeams().toSet()

    /** The art this team is drawn with in this game this season. */
    @Transactional
    fun draw(fielding: TeamSeason, banner: String): TeamSeason {
        fielding.banner = pictures.of(banner, FileType.TEAM_BANNER)
        return fielded.save(fielding)
    }

    /**
     * Stops a team being fielded in a season. The team, and its other seasons, are untouched:
     * a team dropped from one season still played the others.
     */
    @Transactional
    fun unfield(teamId: Long, game: String, seasonId: Long) {
        fielded.findByTeamIdAndGameAndSeasonId(teamId, game, seasonId)?.let { fielded.delete(it) }
    }
}
