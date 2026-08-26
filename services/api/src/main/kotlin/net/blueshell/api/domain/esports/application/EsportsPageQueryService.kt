package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.command.EsportsPageView
import net.blueshell.api.domain.esports.command.RosterMemberView
import net.blueshell.api.domain.esports.command.SeasonView
import net.blueshell.api.domain.esports.command.TeamView
import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.TeamRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Assembles what a game's page shows, for one season.
 *
 * A linked entry is rendered by the member's current handle for the game, so a rename lands
 * on every season at once; an entry nobody is linked to keeps the handle it was published
 * under. Handles for the whole page resolve in one query rather than one per member.
 */
@Service
class EsportsPageQueryService(
    private val rosters: TeamRosterService,
    private val seasons: SeasonService,
    private val accounts: UserGameAccountService,
) {
    @Transactional(readOnly = true)
    fun page(game: Game, seasonId: Long? = null): EsportsPageView {
        val available = rosters.findSeasonIdsWithRosters(game)
            .mapNotNull { id -> runCatching { seasons.findById(id) }.getOrNull() }
            .sortedByDescending { it.startDate }
            .map { it.asView() }

        val season = seasonId?.let { requested -> available.firstOrNull { it.id == requested } }
            ?: available.firstOrNull()
        if (season == null) return EsportsPageView(game, null, available, emptyList())

        val entries = rosters.findByGameAndSeason(game, season.id)
        val handles = accounts.handlesFor(game, entries.mapNotNull { it.userId }.toSet())

        val teams = entries
            .groupBy { it.team }
            .map { (team, members) ->
                TeamView(
                    id = team.id!!,
                    name = team.name,
                    image = team.image,
                    members = members.map { entry ->
                        RosterMemberView(
                            role = entry.teamRole,
                            handle = entry.userId?.let { handles[it] } ?: entry.handle,
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
