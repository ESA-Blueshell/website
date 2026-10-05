package net.blueshell.api.discord.api

/**
 * Opens a channel to a role for reading and writing, and keeps that access as the site's, as the
 * role's own page would. For a module that hands a role its default channels.
 */
interface DiscordRoleAccess {
    fun openForWriting(
        roleId: String,
        channelId: String,
    )
}
