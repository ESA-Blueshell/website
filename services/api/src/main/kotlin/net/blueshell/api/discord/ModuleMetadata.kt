package net.blueshell.api.discord

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The association's Discord server as the site reads it, through the bot: who is in which voice
 * room, which rooms everybody may see or join, and how many are online out of how many members.
 *
 * Two ways in, each for what only it can do. The gateway (JDA) holds the live state: channels,
 * their permissions and who is in voice, which Discord's REST API cannot list. The generated
 * REST client reads the counts. Without a bot token neither starts, and the site falls back to
 * Discord's public widget.
 */
@PackageInfo
@ApplicationModule(
    id = "discord",
    allowedDependencies = [
        // Open kernel.
        "shared",
        // The bot implements the DiscordPublisher port sync declares.
        "sync :: api",
    ],
)
class ModuleMetadata
