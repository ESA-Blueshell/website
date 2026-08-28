package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.Game
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
    val game: Game,
    val name: String,
    @Schema(description = "Asset file name for the team's background image")
    val image: String?,
    @Schema(description = "Where the team's uploaded poster is served, where one was uploaded")
    val posterUrl: String? = null,
)

@Schema(description = "A game: what it is called, the art it is drawn with, and how its page presents it")
data class GamePageResponse(
    val game: Game,
    @Schema(description = "What the pages print for this game")
    val name: String,
    @Schema(description = "The address the game's page answers to")
    val slug: String,
    @Schema(description = "The colour that carries this game, where one has been chosen")
    val accent: String?,
    @Schema(description = "Asset file name for the game's own mark, where it has one")
    val mark: String?,
    @Schema(description = "Asset file name for the image behind the game on the index")
    val banner: String?,
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
    @Schema(description = "Where this entry's uploaded picture is served, where one was uploaded")
    val iconUrl: String? = null,
)

@Schema(description = "A team with the roster it fielded in one season")
data class TeamRosterResponse(
    val id: Long,
    val name: String,
    val image: String?,
    val members: List<RosterMemberResponse>,
    @Schema(description = "Where the team's uploaded poster is served, where one was uploaded")
    val posterUrl: String? = null,
    @Schema(description = "The banner resolved for this team in the season being shown")
    val bannerUrl: String? = null,
)

@Schema(description = "A game's teams for one season, and the seasons that can be shown")
data class EsportsPageResponse(
    val game: Game,
    @Schema(description = "The season being shown; absent when the game has no rosters yet")
    val season: SeasonResponse?,
    val seasons: List<SeasonResponse>,
    val teams: List<TeamRosterResponse>,
    @Schema(description = "The banner for the game and season shown, absent when none is set anywhere")
    val bannerUrl: String? = null,
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
    val season: SeasonResponse,
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
    @Schema(description = "Where this entry's uploaded picture is served, where one was uploaded")
    val iconUrl: String? = null,
)

@Schema(description = "What one member is called in one game")
data class GameAccountResponse(
    val id: Long,
    val userId: Long,
    val game: Game,
    val handle: String,
)

@Schema(description = "A banner and how narrowly it was set, as an admin manages them")
data class EsportsBannerResponse(
    val id: Long,
    val game: Game,
    @Schema(description = "The season it is set for; absent when it carries every season")
    val seasonId: Long? = null,
    @Schema(description = "The team it is set for; absent when it carries every team")
    val teamId: Long? = null,
    @Schema(description = "Where the image is served")
    val url: String,
)
