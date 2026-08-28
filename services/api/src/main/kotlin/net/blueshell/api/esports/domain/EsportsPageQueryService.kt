package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.TeamRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.esports.api.TeamRosterService

/**
 * Assembles what a game's page shows, for one season.
 *
 * A linked entry is rendered by the member's current handle for the game, so a rename lands
 * on every season at once; an entry nobody is linked to keeps the handle it was published
 * under. A real name appears only for a linked member who has said it may, and never for an
 * entry nobody is linked to, whatever name that entry was recorded with.
 *
 * Handles, consent and names each resolve once for the whole page rather than per member.
 */
@Service
class EsportsPageQueryService(
    private val rosters: TeamRosterService,
    private val seasons: SeasonService,
    private val fielded: TeamSeasonService,
    private val accounts: UserGameAccountService,
    private val profiles: MemberProfileService,
    private val users: UserService,
) {
    @Transactional(readOnly = true)
    fun page(game: Game, seasonId: Long? = null): EsportsPageView {
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
        if (season == null) return EsportsPageView(game, null, available, emptyList())

        // The teams are the ones fielded; the roster entries only say who played for them,
        // and a team announced before its line-up was settled has none yet.
        val squads = fielded.findByGameAndSeason(game, season.id)
        val entries = rosters.findByGameAndSeason(game, season.id)
        val linked = entries.mapNotNull { it.userId }.toSet()
        val handles = accounts.handlesFor(game, linked)
        val consenting = profiles.consentingToNameOnTeamPages(linked)
        val names = users.findAllByIds(consenting)
            .mapNotNull { user -> user.id?.let { it to user.fullName } }
            .toMap()

        val byTeam = entries.groupBy { it.team.id }
        val teams = squads
            .map { squad -> squad.team }
            .map { team ->
                TeamView(
                    id = team.id!!,
                    name = team.name,
                    image = team.image,
                    members = byTeam[team.id].orEmpty().map { entry ->
                        RosterMemberView(
                            role = entry.teamRole,
                            handle = entry.userId?.let { handles[it] } ?: entry.handle,
                            name = entry.userId?.let { names[it] },
                            roleTitle = entry.roleTitle,
                            description = entry.description,
                        )
                    },
                )
            }
            .sortedBy { it.name }

        return EsportsPageView(game, season, available, teams)
    }

    private fun Season.asView() =
        SeasonView(id = id!!, name = name, startDate = startDate, endDate = endDate)
}
