package net.blueshell.api.discord.api

/**
 * The roles the site keeps in sync, by Discord id. Implemented by the module that links them, so
 * the bot's check can name only what stands in the site's way and not everything in the server.
 */
fun interface LinkedDiscordRoles {
    fun ids(): Set<String>
}
