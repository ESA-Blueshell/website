package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.discord.domain.DiscordBotSettings
import net.blueshell.api.discord.domain.DiscordSettings
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Where the bots post and which roles the role-claim bot hands out, as the Discord settings page sets them. */
@RestController
@RequestMapping("/management/discord/settings/bot")
@Tag(name = "Discord settings", description = "The Discord roles and defaults the board sets for the whole server")
@BoardOnly
class DiscordBotSettingsController(
    private val settings: DiscordSettings,
) {
    @GetMapping
    fun findDiscordBotSettings(): DiscordBotSettings = settings.read()

    @PutMapping
    fun setDiscordBotSettings(
        @RequestBody request: DiscordBotSettings,
    ): DiscordBotSettings = settings.write(request)
}
