package net.blueshell.api.esports.domain

import net.blueshell.api.file.api.Image
import net.blueshell.api.shared.enums.TeamRole
import java.time.LocalDate

/** One season, as it is named where another can be chosen. */
data class SeasonView(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/**
 * One person on a roster, as the public read may show them.
 *
 * The handle is always there. [name] is present only for a linked member who has said their
 * real name may be published: names are held in the database to identify people, and putting
 * one in public is a decision each member makes for themselves.
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
    val icon: Image? = null,
)

data class TeamView(
    val id: Long,
    val name: String,
    val members: List<RosterMemberView>,
    /** The team's own banner. */
    val banner: Image? = null,
    /** The team's own icon, shown beside the name. */
    val icon: Image? = null,
)

/** A game's rosters: the season being shown, the ones that can be, and that season's teams. */
data class GameRostersView(
    val game: String,
    val season: SeasonView?,
    val seasons: List<SeasonView>,
    val teams: List<TeamView>,
)

/**
 * A game that ran in one season, with what it fielded.
 *
 * [public] is what a visitor would see: a game is public in a season once a team plays it. A
 * game entered with nobody fielded is answered only to somebody who may edit, and is marked so
 * a caller can say it is not public yet rather than quietly showing it as though it were.
 */
data class SeasonGameView(
    val game: String,
    val teams: List<TeamView>,
    val public: Boolean,
)
