package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.entities.Guild

/**
 * The server as the gateway holds it, which Discord's events keep current: its roles and channels,
 * and the @everyone overrides on each channel. Null until the gateway has the server.
 */
fun interface GatewayGuild {
    fun guild(): Guild?
}
