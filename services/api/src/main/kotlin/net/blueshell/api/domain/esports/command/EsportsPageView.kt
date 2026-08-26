package net.blueshell.api.domain.esports.command

import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.TeamRole
import java.time.LocalDate

/** One season, as a page names it in its switcher. */
data class SeasonView(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/**
 * One person on a roster, as the public page may show them.
 *
 * Carries the handle and nothing that identifies the member behind it: real names are held
 * in the database for identification, and publishing one is a separate decision the member
 * makes for themselves.
 */
data class RosterMemberView(
    val role: TeamRole,
    val handle: String,
)

data class TeamView(
    val id: Long,
    val name: String,
    val image: String?,
    val members: List<RosterMemberView>,
)

/** A whole game page: the season being shown, the ones that can be, and that season's teams. */
data class EsportsPageView(
    val game: Game,
    val season: SeasonView?,
    val seasons: List<SeasonView>,
    val teams: List<TeamView>,
)
