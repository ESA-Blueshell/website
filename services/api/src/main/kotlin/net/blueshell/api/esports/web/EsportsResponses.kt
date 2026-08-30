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
)

@Schema(description = "A team the association fields in one game")
data class TeamResponse(
    val id: Long,
    val name: String,
    @Schema(description = "The team's own icon, drawn in its slice beside the name. The banner it is drawn on belongs to the fielding, not to the team")
    val icon: Image? = null,
)

@Schema(description = "A team fielded in a game in a season, which is where a line-up hangs")
data class FieldingResponse(
    val game: String,
    val season: SeasonResponse,
)

@Schema(description = "A game: what it is called, the art it is drawn with, and how its page presents it")
data class GamePageResponse(
    val game: String,
    @Schema(description = "What the pages print for this game")
    val name: String,
    @Schema(description = "The address the game's page answers to")
    val slug: String,
    @Schema(description = "The colour that carries this game, where one has been chosen")
    val accent: String?,
    @Schema(description = "The game's own image, drawn in the slice for it on the esports index")
    val banner: Image? = null,
    @Schema(description = "The game's own icon, drawn in that slice beside the name")
    val icon: Image? = null,
    @Schema(description = "What the page says about the game, where anything is said")
    val intro: String?,
    @Schema(description = "Where the game sits among the others")
    val sortIndex: Int,
    @Schema(description = "Whether the association still fields a team in it")
    val fielded: Boolean,
)

@Schema(description = "One person on a team's roster, as the public pages show them")
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
    @Schema(description = "The team's own banner, drawn in the slice for it")
    val banner: Image? = null,
    @Schema(description = "The team's own icon, drawn in that slice beside the name")
    val icon: Image? = null,
)

@Schema(description = "A game's teams for one season, and the seasons that can be shown")
data class EsportsPageResponse(
    val game: String,
    @Schema(description = "The season being shown; absent when the game has no rosters yet")
    val season: SeasonResponse?,
    val seasons: List<SeasonResponse>,
    val teams: List<TeamRosterResponse>,
)

@Schema(description = "What a game holds, for a removal to say before it happens")
data class GameContentsResponse(
    @Schema(description = "Teams recorded in it, across every season")
    val teams: Int,
    @Schema(description = "Roster places those teams carry")
    val players: Int,
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

