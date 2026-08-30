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

@Schema(description = "Create a team. A team is the association's rather than a game's, so it names none")
data class CreateTeamRequest(
    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @Schema(description = "Where the team's icon is stored; nothing leaves the team without one")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val icon: String? = null,
)

@Schema(description = "Rename a team or change its icon. Its banner belongs to the fielding")
data class UpdateTeamRequest(
    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @Schema(description = "Where the team's icon is stored; nothing takes the icon away")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val icon: String? = null,
)

@Schema(description = "Field a team in a game in a season, with or without the line-up it last had")
data class FieldTeamRequest(
    @field:NotBlank(message = "Game is required")
    @field:Size(min = 1, max = 32)
    @Schema(description = "The game it is being fielded in. A team may play more than one in a season")
    val game: String,

    @Schema(description = "Copy the line-up this team last had in this game into this season")
    val carryLineup: Boolean = false,

    /**
     * The art this team is drawn with in this game this season.
     *
     * Applied when it is named and left alone when it is not, which is not the rule the other
     * saves here follow. Fielding is idempotent and is called to say "this team plays this
     * season" as often as it is called to change a picture, so treating an unnamed banner as
     * "take the art away" would strip a season's art every time somebody re-fielded a team.
     * A fielding that is new takes the art of the last season this team played this game.
     */
    @Schema(description = "Where the art for this fielding is stored; nothing leaves what it has")
    @field:Size(max = 255, message = "Picture must be at most 255 characters")
    val banner: String? = null,
)

@Schema(description = "Put somebody on a team's roster for a season")
data class AddRosterEntryRequest(
    @field:NotBlank(message = "Game is required")
    @field:Size(min = 1, max = 32)
    @Schema(description = "The game the team is fielded in; naming somebody fields it there")
    val game: String,

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
    @field:Schema(description = "Where the game's icon is stored; nothing takes the icon away")
    val icon: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Where the game's banner is stored; nothing takes the banner away")
    val banner: String? = null,
    val sortIndex: Int = 0,
    @field:Schema(description = "Whether the association still fields a team in it")
    val fielded: Boolean = true,
)
