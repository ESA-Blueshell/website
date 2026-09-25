package net.blueshell.api.game.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.web.HEX_COLOUR
import net.blueshell.api.shared.web.HEX_COLOUR_REFUSED

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
    @field:Size(max = 4000)
    @field:Schema(description = "What the competition pages say; blank to say the intro")
    val competitionIntro: String? = null,
    @field:Pattern(regexp = HEX_COLOUR, message = HEX_COLOUR_REFUSED)
    @field:Schema(description = "The colour that carries this game, as # and six hex digits, or nothing for the island's own")
    val accent: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Where the game's banner is stored; nothing takes it away")
    val banner: String? = null,
    @field:Size(max = 255)
    @field:Schema(description = "Where the game's icon is stored; nothing takes it away")
    val icon: String? = null,
    @field:Valid
    @field:Size(max = 20)
    @field:Schema(description = "The Discord channels it lives in; left out, the ones it has are kept")
    val channels: List<GameChannelRequest>? = null,
    @field:Valid
    @field:Size(max = 20)
    @field:Schema(description = "The Discord channels its esports players meet in; left out, the ones it has are kept")
    val esportsChannels: List<GameChannelRequest>? = null,
    @field:Schema(description = "Where the game sits among the others; left out, a new game goes last and a game keeps its place")
    val sortIndex: Int? = null,
)

@Schema(description = "A Discord channel a game lives in, as the picker offered it")
data class GameChannelRequest(
    @field:NotBlank
    @field:Size(max = 32)
    val id: String,
    @field:NotBlank
    @field:Size(max = 32)
    val guildId: String,
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
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
    val players: Long,
)
