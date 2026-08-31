package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.file.api.asImage
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.shared.enums.TeamRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.esports.api.TeamRosterService

/**
 * Assembles what is published for one game, for one season.
 *
 * A linked entry is rendered by the member's current handle for the game, so a rename lands
 * on every season at once; an entry nobody is linked to keeps the handle it was published
 * under. A real name appears only for a linked member who has said it may, and never for an
 * entry nobody is linked to, whatever name that entry was recorded with.
 *
 * Handles, consent and names each resolve once for the whole read rather than per member.
 */
@Service
class EsportsQueryService(
    private val rosters: TeamRosterService,
    private val seasons: SeasonService,
    private val fielded: TeamSeasonService,
    private val accounts: UserGameAccountService,
    private val profiles: MemberProfileService,
    private val users: UserService,
    private val games: GameService,
    private val entered: SeasonGameService,
) {
    /**
     * Every game that ran in one season, with what it fielded.
     *
     * One read for the whole season rather than one per game, and the place the rule about what
     * is public in a season is applied: a game is public once a team plays it. A game entered
     * with nobody fielded is answered only where [mayEdit], marked as not public, so the board
     * can see what it has not finished and a visitor sees a season that is not half-built.
     *
     * The rule is here rather than in the frontend because it turns on who is asking, and a rule
     * that turns on who is asking cannot be a condition in a template.
     */
    @Transactional(readOnly = true)
    fun gamesOf(seasonId: Long, mayEdit: Boolean): List<SeasonGameView> {
        val played = games.codes().mapNotNull { code ->
            val teams = teamsOf(code, seasonId)
            if (teams.isEmpty()) null else SeasonGameView(code, teams, public = true)
        }
        if (!mayEdit) return played
        val shown = played.map { it.game }.toSet()
        val quiet = entered.gamesIn(seasonId)
            .filter { it !in shown }
            .map { SeasonGameView(it, emptyList(), public = false) }
        return (played + quiet).sortedBy { view -> games.codes().indexOf(view.game) }
    }

    @Transactional(readOnly = true)
    fun rostersOf(game: String, seasonId: Long? = null): GameRostersView {
        // A code naming no game is refused rather than answered with nothing.
        games.requireGame(game)
        val available = fielded.findSeasonIdsFielded(game)
            .mapNotNull { id -> runCatching { seasons.findById(id) }.getOrNull() }
            .sortedByDescending { it.startDate }
            .map { it.asView() }

        // A season that was asked for by name is shown even where this game fielded nobody in
        // it: a season has to be reachable before a team can be added to it, and the answer
        // for a season with no teams is that it had none, not a different season's teams.
        val season = seasonId?.let { requested ->
            available.firstOrNull { it.id == requested }
                ?: runCatching { seasons.findById(requested) }.getOrNull()?.asView()
        } ?: available.firstOrNull()
        if (season == null) return GameRostersView(game, null, available, emptyList())

        return GameRostersView(game, season, available, teamsOf(game, season.id))
    }

    /**
     * What one game fielded in one season: the teams, and who played for each.
     *
     * The teams are the ones fielded; the roster entries only say who played for them, and a
     * team announced before its line-up was settled has none yet. Shared by the read for one
     * game and the read for a whole season, so both answer the same thing.
     */
    private fun teamsOf(game: String, seasonId: Long): List<TeamView> {
        val squads = fielded.findByGameAndSeason(game, seasonId)
        if (squads.isEmpty()) return emptyList()
        val entries = rosters.findByGameAndSeason(game, seasonId)
        val linked = entries.mapNotNull { it.userId }.toSet()
        val handles = accounts.handlesFor(game, linked)
        val consenting = profiles.consentingToNameOnRosters(linked)
        val names = users.findAllByIds(consenting)
            .mapNotNull { user -> user.id?.let { it to user.fullName } }
            .toMap()

        val byTeam = entries.groupBy { it.teamId }
        return squads
            .map { squad ->
                val team = squad.team
                TeamView(
                    id = team.id!!,
                    name = team.name,
                    members = byTeam[team.id].orEmpty().map { entry ->
                        RosterMemberView(
                            role = entry.teamRole,
                            handle = entry.userId?.let { handles[it] } ?: entry.handle,
                            name = entry.userId?.let { names[it] },
                            roleTitle = entry.roleTitle,
                            description = entry.description,
                            icon = entry.icon?.asImage(),
                        )
                    },
                    banner = squad.banner?.asImage(),
                    icon = team.icon?.asImage(),
                )
            }
            .sortedBy { it.name }
    }

    private fun Season.asView() =
        SeasonView(id = id!!, name = name, startDate = startDate, endDate = endDate)
}
