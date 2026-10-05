package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.file.api.Image
import net.blueshell.api.shared.enums.TeamRole
import java.time.LocalDate

@Schema(description = "A stretch of play that rosters belong to")
data class SeasonResponse(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    @Schema(description = "Whether anything was fielded in it, which is which seasons a visitor is offered")
    val played: Boolean = false,
)

@Schema(description = "A game that ran in a season, with what it fielded")
data class SeasonGameResponse(
    val game: String,
    val teams: List<TeamRosterResponse>,
    @Schema(description = "Whether a visitor sees it: a game is public in a season once a team plays it")
    val public: Boolean,
)

@Schema(description = "A team the association fields in one game")
data class TeamResponse(
    val id: Long,
    val name: String,
    @Schema(description = "The team's own icon, drawn beside the name. The banner it is drawn on belongs to the fielding, not to the team")
    val icon: Image?,
    @Schema(description = "Whether the team stopped playing", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    val archived: Boolean,
)

@Schema(name = "ArchiveTeamRequest", description = "Whether a team stopped playing")
data class ArchiveTeamRequest(
    val archived: Boolean,
)

@Schema(description = "A team fielded in a game in a season, which is where a line-up hangs")
data class FieldingResponse(
    val game: String,
    val season: SeasonResponse,
)

@Schema(description = "One team's fielding in a game in a season, named by the team it belongs to")
data class TeamFieldingResponse(
    val teamId: Long,
    val game: String,
    val season: SeasonResponse,
)

@Schema(description = "One person on a team's roster, as the public read has them")
data class RosterMemberResponse(
    val role: TeamRole,
    @Schema(description = "What this member is called in the game")
    val handle: String,
    @Schema(description = "The member's real name, present only when they allow it to be shown")
    val name: String? = null,
    @Schema(description = "What they did in the team's own words, where anything was said")
    val roleTitle: String? = null,
    @Schema(description = "A short caption about them, in markdown, where anything was written")
    val description: String? = null,
    @Schema(description = "This entry's uploaded picture, where one was uploaded")
    val icon: Image? = null,
)

@Schema(description = "A team with the roster it fielded in one season")
data class TeamRosterResponse(
    val id: Long,
    val name: String,
    val members: List<RosterMemberResponse>,
    @Schema(description = "The team's own banner")
    val banner: Image? = null,
    @Schema(description = "The team's own icon, shown beside the name")
    val icon: Image? = null,
)

@Schema(description = "A game's teams for one season, and the seasons that can be shown")
data class GameRostersResponse(
    val game: String,
    @Schema(description = "The season being shown; absent when the game has no rosters yet")
    val season: SeasonResponse?,
    val seasons: List<SeasonResponse>,
    val teams: List<TeamRosterResponse>,
)

@Schema(description = "What a season holds, for a removal to say before it happens")
data class SeasonContentsResponse(
    @Schema(description = "Teams fielded in it, across every game")
    val teams: Int,
    @Schema(description = "Roster places held in it, across those teams")
    val players: Int,
)

@Schema(description = "A team now fielded in a season, and whatever line-up came across with it")
data class FieldedTeamResponse(
    val team: TeamResponse,
    @Schema(description = "The game it was fielded in")
    val game: String,
    val season: SeasonResponse,
    @Schema(description = "The art it is drawn with this season, carried across from the last one it played")
    val banner: Image? = null,
    @Schema(description = "The entries copied from the team's last season; empty when nothing was carried")
    val carried: List<RosterEntryResponse>,
)

@Schema(description = "A roster entry as an admin edits it, real name included")
data class RosterEntryResponse(
    val id: Long,
    val teamId: Long,
    val seasonId: Long,
    val role: TeamRole,
    val handle: String,
    @Schema(description = "The member's real name, shown to admins for identification")
    val displayName: String?,
    @Schema(description = "The member this entry belongs to, when anybody could be identified")
    val userId: Long?,
    val sortIndex: Int,
    @Schema(description = "What they did in the team's own words")
    val roleTitle: String? = null,
    @Schema(description = "A short caption about them, in markdown")
    val description: String? = null,
    @Schema(description = "This entry's uploaded picture, where one was uploaded")
    val icon: Image? = null,
)

@Schema(description = "What one member is called in one game")
data class GameAccountResponse(
    val id: Long,
    val userId: Long,
    val game: String,
    val handle: String,
)

/** One roster spot somebody held: which game, season and team, and what they did in it. */
@Schema(name = "PlayedRosterResponse")
data class PlayedRosterResponse(
    val game: String,
    val seasonId: Long,
    val seasonName: String,
    val seasonStart: LocalDate,
    val teamId: Long,
    val teamName: String,
    val role: TeamRole,
    val roleTitle: String?,
)

@Schema(description = "A line-up as saved: the team as it now stands and its roster in order")
data class PublishedLineupResponse(
    val team: TeamResponse,
    val roster: List<RosterEntryResponse>,
)
