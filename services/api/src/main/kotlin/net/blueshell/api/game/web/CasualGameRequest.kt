package net.blueshell.api.game.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "A game as the board adds or corrects it from the casual pages")
data class CasualGameRequest(
    @field:NotBlank(message = "A game needs a name")
    @field:Size(max = 64, message = "Name must be at most 64 characters")
    @field:Schema(description = "What this game is called. Its code is taken from it once, when it is added")
    val name: String,
    @field:NotBlank(message = "A game's page needs an address")
    @field:Size(max = 64, message = "Address must be at most 64 characters")
    @field:Schema(description = "The address this game answers to under /casual")
    val slug: String,
    @field:Size(max = 4000)
    val intro: String? = null,
    @field:Size(max = 32)
    @field:Schema(description = "The colour that carries this game, or nothing for the island's own")
    val accent: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Where the game's banner is stored; nothing takes it away")
    val banner: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Where the game's icon is stored; nothing takes it away")
    val icon: String? = null,
)

@Schema(description = "Whether nobody plays a game casually any more")
data class ArchiveGameRequest(
    val archived: Boolean,
)

@Schema(description = "What removing a game would touch, so the offer to remove it can say so")
data class GameHoldingsResponse(
    @Schema(description = "Discord channels the game lives in")
    val channels: Long,
    @Schema(description = "Committees linked to the game")
    val committees: Long,
    @Schema(description = "Events that name the game")
    val events: Long,
    @Schema(description = "Teams fielded in the game; a game with any cannot be removed")
    val teams: Long,
    @Schema(description = "People on those teams' line-ups")
    val people: Long,
)
