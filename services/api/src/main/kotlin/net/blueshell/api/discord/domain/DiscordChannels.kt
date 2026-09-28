package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** A channel everybody in the server can see, which a description writes as `<#id>`. */
data class DiscordChannel(
    val id: String,
    val name: String,
)

/**
 * The channels everybody in the server can see, in the order the server lists them, as the gateway
 * holds them. A channel hidden from @everyone is never named, so a mention of one reads as Discord
 * shows it to an outsider. Null without a bot, or while the gateway has never had the server; the
 * last channels it had serve while it is away.
 */
@Service
class DiscordChannelDirectory(
    private val gateway: ObjectProvider<GatewayGuild>,
    clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<List<DiscordChannel>>("Discord channels", Duration.ZERO, clock)

    fun open(): List<DiscordChannel>? {
        val source = gateway.ifAvailable ?: return null
        return kept.get { source.guild()?.let(::seenByEveryone) }
    }

    private fun seenByEveryone(guild: Guild): List<DiscordChannel> {
        val everyone = guild.publicRole

        // The channel's own rule for @everyone decides; without one, its category's does.
        fun hidden(channel: GuildChannel): Boolean {
            val own = (channel as? IPermissionContainer)?.getPermissionOverride(everyone)
            val rule = own ?: (channel as? ICategorizableChannel)?.parentCategory?.getPermissionOverride(everyone)
            return rule?.denied?.contains(Permission.VIEW_CHANNEL) ?: false
        }
        return guild.channels
            .filter { it !is Category && !hidden(it) }
            .map { DiscordChannel(it.id, it.name) }
    }
}
