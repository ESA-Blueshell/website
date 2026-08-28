package net.blueshell.api.domain.esports.application

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
 * The handle is always there. [name] is present only for a linked member who has said their
 * real name may be published: names are held in the database to identify people, and putting
 * one on a page is a decision each member makes for themselves.
 */
data class RosterMemberView(
    val role: TeamRole,
    val handle: String,
    val name: String? = null,
    /** What they did, in the team's own words, where anything was said. */
    val roleTitle: String? = null,
    /** A caption about them, in markdown, where anything was written. */
    val description: String? = null,
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
