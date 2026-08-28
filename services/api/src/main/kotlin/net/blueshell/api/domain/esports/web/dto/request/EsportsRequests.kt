package net.blueshell.api.domain.esports.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.enums.Game
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
    val game: Game,

    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    val image: String? = null,
)

@Schema(description = "Rename a team or change its image")
data class UpdateTeamRequest(
    @field:NotBlank(message = "Team name is required")
    @field:Size(min = 1, max = 128, message = "Name must be 1-128 characters")
    val name: String,

    @field:Size(max = 255, message = "Image must be at most 255 characters")
    val image: String? = null,
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

@Schema(name = "UpdateGamePageRequest", description = "How a game presents itself")
data class UpdateGamePageRequest(
    @field:NotBlank
    @field:Size(max = 64)
    @field:Schema(description = "The address the game's page answers to")
    val slug: String,
    @field:Size(max = 4000)
    val intro: String? = null,
    val sortIndex: Int = 0,
    @field:Schema(description = "Whether the association still fields a team in it")
    val fielded: Boolean = true,
)
