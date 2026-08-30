package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.shared.enums.FileType
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Which teams the association fielded in which game, in which season.
 *
 * Fielding a team and naming its players are two different decisions taken at two different
 * times, so this answers the first without waiting for the second.
 *
 * Every question here names a game as well as a team, because a team is not a game's: the same
 * team can be fielded in two games in one season, and each fielding has a line-up and art of its
 * own. A code naming no game is refused before anything is written.
 */
@Service
class TeamSeasonService(
    private val fielded: TeamSeasonRepository,
    private val entries: TeamRosterEntryRepository,
    private val teams: TeamService,
    private val seasons: SeasonService,
    private val games: GamePageService,
    private val pictures: EsportsPictures,
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
     * A team fielded again in a season it was dropped from revives the fielding it had rather
     * than writing a second one. The line-up hangs off the fielding, so a second row would
     * leave last time's line-up attached to the dropped one -- present in the table, reachable
     * by nothing, and silently absent from the season it was played in.
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
                game = games.requireGame(game).game,
                season = seasons.findById(seasonId),
                // The art a team was last drawn with in this game comes across, so a season is
                // only asked for a picture when the picture should change. A team playing a game
                // for the first time starts without one, which is what a new team looks like.
                banner = fielded.findPreviousInGame(teamId, game, seasonId).firstOrNull()?.banner,
            ),
        )
    }

    /**
     * The games the association currently plays: what was fielded in the season we are in, and
     * in the season before it.
     *
     * A union rather than the newest season alone. A season is set up a game at a time, so a
     * list that followed only the newest would collapse to whichever game the board entered
     * first and refill as they worked — a half-finished season, in public, every changeover.
     * The cost is accepted and known: a game the association has genuinely stopped playing
     * stays listed for one more season, which is a far smaller lie than the association
     * playing one game.
     *
     * Fielded, not merely entered: a game is public in a season once a team plays it, which is
     * the same rule the pages themselves answer to.
     */
    @Transactional(readOnly = true)
    fun currentlyPlayed(on: LocalDate = LocalDate.now()): Set<String> {
        val ordered = seasons.findAll()
        // Where no season covers today — a gap between them — the most recent one that has
        // started is the one we are in for this purpose.
        val current = seasons.findCurrent(on) ?: ordered.firstOrNull { !it.startDate.isAfter(on) }
            ?: return emptySet()
        val previous = ordered.firstOrNull { it.startDate.isBefore(current.startDate) }
        return fielded.gamesFieldedIn(listOfNotNull(current.id, previous?.id)).toSet()
    }

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
