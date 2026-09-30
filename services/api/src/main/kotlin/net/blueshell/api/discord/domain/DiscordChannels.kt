package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.concrete.Category
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** A channel everybody in the server can see, which a description writes as `<#id>`. */
data class DiscordChannel(
    val id: String,
    val name: String,
    /** The category the server files it under, null for a channel outside any. */
    val category: String?,
)

/**
 * The channels everybody in the server can see, in the order the server lists them, as the gateway
 * holds them. A channel @everyone cannot view is never named, so a mention of one reads as Discord
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

    // What @everyone may see once the server's base permissions and the channel's overrides are
    // applied, as Discord itself works it out.
    private fun seenByEveryone(guild: Guild): List<DiscordChannel> =
        guild.channels
            .filter { it !is Category && guild.publicRole.hasPermission(it, Permission.VIEW_CHANNEL) }
            .map { DiscordChannel(it.id, it.name, (it as? ICategorizableChannel)?.parentCategory?.name) }
}
