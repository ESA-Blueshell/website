package net.blueshell.api.esports.domain

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
    /** This entry's own picture, where one was uploaded. */
    val iconFileId: Long? = null,
)

data class TeamView(
    val id: Long,
    val name: String,
    val image: String?,
    val members: List<RosterMemberView>,
    /** The team's own poster, where one was uploaded. */
    val posterFileId: Long? = null,
    /** The banner resolved for this team in the season being shown. */
    val bannerFileId: Long? = null,
)

/** A whole game page: the season being shown, the ones that can be, and that season's teams. */
data class EsportsPageView(
    val game: String,
    val season: SeasonView?,
    val seasons: List<SeasonView>,
    val teams: List<TeamView>,
    /** The banner resolved for the game and the season being shown, before any team narrows it. */
    val bannerFileId: Long? = null,
)
