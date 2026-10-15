package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.discord.domain.BotStanding
import net.blueshell.api.discord.domain.BotStandingResult
import net.blueshell.api.discord.domain.CataloguedChannel
import net.blueshell.api.discord.domain.DiscordCatalogue
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/management/discord")
@Tag(name = "Discord management", description = "What the site keeps in sync on Discord")
@BoardOnly
class DiscordBotController(
    private val standing: BotStanding,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val catalogue: DiscordCatalogue,
) {
    /** The roles the site could keep, for a picker; none without a bot. */
    @GetMapping("/roles")
    fun listKeptRoles(): List<KeptRole> = if (roles.available()) roles.roles() else emptyList()

    /** Every channel and category, for a picker; none without a bot. */
    @GetMapping("/channels")
    fun listKeptChannels(): List<KeptChannel> = if (channels.available()) channels.channels() else emptyList()

    /** Every channel with who it is opened to and the game it is kept for; none without a bot. */
    @GetMapping("/catalogue")
    fun listCataloguedChannels(): List<CataloguedChannel> = catalogue.channels()

    /** Whether the bot may manage roles and channels, and which roles stay out of its hands. */
    @GetMapping("/bot")
    fun findBotStanding(): BotStandingResult = standing.read()
}
