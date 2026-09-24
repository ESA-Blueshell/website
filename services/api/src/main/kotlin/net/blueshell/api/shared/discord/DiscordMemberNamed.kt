package net.blueshell.api.shared.discord

/**
 * A member of the association's Discord server is called [name] there now: their nickname in the
 * server, else their display name, else their username. Published by the discord module, and read
 * by the user module to rename the account linked to [discordId].
 */
data class DiscordMemberNamed(
    val discordId: String,
    val name: String,
)
