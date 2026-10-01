package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.discord.domain.AccessPolicy
import net.blueshell.api.discord.domain.BotStanding
import net.blueshell.api.discord.domain.BotStandingResult
import net.blueshell.api.discord.domain.CataloguedChannel
import net.blueshell.api.discord.domain.ChannelAccessState
import net.blueshell.api.discord.domain.DiscordCatalogue
import net.blueshell.api.discord.domain.DiscordUnreachable
import net.blueshell.api.discord.domain.GameAccess
import net.blueshell.api.discord.domain.GameAccessState
import net.blueshell.api.discord.domain.GameChannelCategory
import net.blueshell.api.discord.domain.GameChannelPolicies
import net.blueshell.api.discord.domain.MadeChannel
import net.blueshell.api.security.BoardOnly
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@io.swagger.v3.oas.annotations.media.Schema(name = "CreateGameChannelRequest")
data class CreateGameChannelRequest(
    val name: String,
    val category: GameChannelCategory = GameChannelCategory.GAMES,
)

@RestController
@RequestMapping("/management/discord")
@Tag(name = "Discord management", description = "What the site keeps in step on Discord")
@BoardOnly
class DiscordBotController(
    private val standing: BotStanding,
    private val gameChannels: GameChannelPolicies,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val catalogue: DiscordCatalogue,
    private val gameAccess: GameAccess,
) {
    /** The roles the site could keep, for a picker; none without a bot. */
    @GetMapping("/roles")
    fun listKeptRoles(): List<KeptRole> = if (roles.available()) roles.roles() else emptyList()

    /** Every channel and category, for a picker; none without a bot. */
    @GetMapping("/channels")
    fun listKeptChannels(): List<KeptChannel> = if (channels.available()) channels.channels() else emptyList()

    /** Every channel with who it is opened to, the game it belongs to and its access, for the Discord page. */
    @GetMapping("/catalogue/channels")
    fun listCataloguedChannels(): List<CataloguedChannel> = catalogue.channels()

    /** Makes a games or esports channel with the default access, for a game's form to add. */
    @PostMapping("/game-channels")
    fun createGameChannel(
        @RequestBody request: CreateGameChannelRequest,
    ): MadeChannel = gameChannels.create(request.name, request.category)

    /** A channel's access as the site keeps it and as Discord has it, and whether they differ. */
    @GetMapping("/channels/{id}/access")
    fun findChannelAccess(
        @PathVariable id: String,
    ): ChannelAccessState = gameChannels.read(id)

    /** Keeps and writes a channel's access; Discord is written now and never again on its own. */
    @PutMapping("/channels/{id}/access")
    fun setChannelAccess(
        @PathVariable id: String,
        @RequestBody policy: AccessPolicy,
    ): ChannelAccessState = gameChannels.set(id, policy)

    /** Who reads every channel of a game and who writes in it, and each channel as Discord has it. */
    @GetMapping("/games/{code}/access")
    fun findGameAccess(
        @PathVariable code: String,
    ): GameAccessState = gameAccess.read(code)

    /** Sets who reads and writes every channel of a game; Discord is written now and never again on its own. */
    @PutMapping("/games/{code}/access")
    fun setGameAccess(
        @PathVariable code: String,
        @RequestBody policy: AccessPolicy,
    ): GameAccessState = gameAccess.set(code, policy)

    /** Moves a channel into the archive category, read only and kept for its history. */
    @PostMapping("/channels/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveChannel(
        @PathVariable id: String,
    ) {
        if (!channels.available()) throw DiscordUnreachable()
        channels.archive(listOf(id))
    }

    /** Whether the bot may manage roles and channels, and which roles stay out of its hands. */
    @GetMapping("/bot")
    fun findBotStanding(): BotStandingResult = standing.read()
}
