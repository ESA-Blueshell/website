package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.TeamRole
import java.time.LocalDate

@Schema(description = "Create or rename a season")
data class SeasonRequest(
    @field:NotBlank(message = "Season name is required")
    @field:Size(min = 1, max = 64, message = "Name must be 1-64 characters")
    val name: String,

    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate,

    @field:NotNull(message = "End date is required")
    val endDate: LocalDate,
)

@Schema(description = "Create a team for a game")
data class CreateTeamRequest(
    @field:NotNull(message = "Game is required")
    val game: String,

    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    val image: String? = null,

    @Schema(description = "Where the team's poster is stored; nothing leaves the team without one")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val poster: String? = null,
)

@Schema(description = "Rename a team or change the pictures it is drawn with")
data class UpdateTeamRequest(
    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    val image: String? = null,

    @Schema(description = "Where the team's poster is stored; nothing takes the poster away")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val poster: String? = null,
)

@Schema(description = "Field a team in a season, with or without the line-up it last had")
data class FieldTeamRequest(
    @Schema(description = "Copy the team's most recent line-up into this season")
    val carryLineup: Boolean = false,
)

@Schema(description = "Put somebody on a team's roster for a season")
data class AddRosterEntryRequest(
    @field:NotNull(message = "Season id is required")
    val seasonId: Long,

    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    val handle: String,

    @field:NotNull(message = "Role is required")
    val role: TeamRole,

    @Schema(description = "The member this entry belongs to, when they are known")
    val userId: Long? = null,

    @field:Size(max = 128, message = "Name must be at most 128 characters")
    val displayName: String? = null,

    @Schema(description = "What they did in the team's own words, beside the fixed part")
    @field:Size(max = 64, message = "Role must be at most 64 characters")
    val roleTitle: String? = null,

    @Schema(description = "A short caption about them, in markdown")
    @field:Size(max = 280, message = "Description must be at most 280 characters")
    val description: String? = null,

    @Schema(description = "Where this entry's picture is stored; nothing leaves it without one")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val icon: String? = null,
)

@Schema(description = "Edit a roster entry")
data class UpdateRosterEntryRequest(
    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    val handle: String,

    @field:NotNull(message = "Role is required")
    val role: TeamRole,

    @field:Size(max = 128, message = "Name must be at most 128 characters")
    val displayName: String? = null,

    @Schema(description = "What they did in the team's own words, beside the fixed part")
    @field:Size(max = 64, message = "Role must be at most 64 characters")
    val roleTitle: String? = null,

    @Schema(description = "A short caption about them, in markdown")
    @field:Size(max = 280, message = "Description must be at most 280 characters")
    val description: String? = null,

    @field:NotNull(message = "Order is required")
    val sortIndex: Int,

    @Schema(description = "Where this entry's picture is stored; nothing takes the picture away")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val icon: String? = null,
)

@Schema(description = "Attach a roster entry to a member, or detach it with a null id")
data class LinkRosterEntryRequest(
    val userId: Long? = null,
)

@Schema(description = "Set what a member is called in one game")
data class GameAccountRequest(
    @field:NotBlank(message = "Handle is required")
    @field:Size(min = 1, max = 128, message = "Handle must be 1-128 characters")
    val handle: String,
)

@Schema(name = "CreateGameRequest", description = "A game the association has started playing")
data class CreateGameRequest(
    @field:NotBlank(message = "A game needs a name")
    @field:Size(min = 1, max = 64, message = "Name must be 1-64 characters")
    val name: String,

    @field:NotBlank(message = "A game's page needs an address")
    @field:Size(min = 1, max = 64, message = "Address must be 1-64 characters")
    @field:Schema(description = "The address the game's page answers to")
    val slug: String,
)

@Schema(name = "UpdateGamePageRequest", description = "How a game presents itself")
data class UpdateGamePageRequest(
    @field:NotBlank(message = "A game needs a name")
    @field:Size(min = 1, max = 64, message = "Name must be 1-64 characters")
    @field:Schema(description = "What the pages print for this game. Its code is not editable")
    val name: String,
    @field:NotBlank
    @field:Size(max = 64)
    @field:Schema(description = "The address the game's page answers to")
    val slug: String,
    @field:Size(max = 4000)
    val intro: String? = null,
    @field:Size(max = 32)
    @field:Schema(description = "The colour that carries this game, or nothing for the island's own")
    val accent: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Asset file name for the game's own mark")
    val mark: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Asset file name for the image behind the game on the index")
    val banner: String? = null,
    val sortIndex: Int = 0,
    @field:Schema(description = "Whether the association still fields a team in it")
    val fielded: Boolean = true,
)

/**
 * Puts a stored picture behind one combination of game, season and team.
 *
 * Naming neither a season nor a team sets the game's own, which is what every page falls back
 * to. The picture is named rather than carried: it was stored when it was chosen.
 */
@Schema(name = "SetBannerRequest", description = "The picture behind one game, season or team")
data class SetBannerRequest(
    @field:NotBlank(message = "A banner is always for a game")
    val game: String,

    @Schema(description = "The season it is for; nothing carries every season")
    val seasonId: Long? = null,

    @Schema(description = "The team it is for; nothing carries every team")
    val teamId: Long? = null,

    @field:NotBlank(message = "A banner needs a picture")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    @field:Schema(description = "Where the picture is stored")
    val picture: String,
)
