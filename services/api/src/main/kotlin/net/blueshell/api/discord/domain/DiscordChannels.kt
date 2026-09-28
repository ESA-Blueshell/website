package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.blueshell.clients.discord.model.ListGuildChannels200ResponseInner
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A channel everybody in the server can see, which a description writes as `<#id>`. */
data class DiscordChannel(
    val id: String,
    val name: String,
)

/**
 * The channels everybody in the server can see, in the server's order. A channel hidden from
 * @everyone is never named, so a mention of one reads as Discord shows it to an outsider. Null
 * without a bot, or where Discord did not answer and nothing was kept.
 */
@Service
class DiscordChannelDirectory(
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
    internal var clock: Clock = Clock.systemUTC(),
) {
    @Volatile private var kept: Pair<Instant, List<DiscordChannel>>? = null

    fun open(): List<DiscordChannel>? {
        val client = api.ifAvailable ?: return null
        val now = clock.instant()
        kept?.let { (at, channels) -> if (Duration.between(at, now) < KEPT_FOR) return channels }
        return runCatching { client.listGuildChannels(guildId) }
            .onFailure { log.warn("Discord channels could not be read", it) }
            .getOrNull()
            ?.let(::seenByEveryone)
            ?.also { kept = now to it }
            ?: kept?.second
    }

    private fun seenByEveryone(channels: List<ListGuildChannels200ResponseInner>): List<DiscordChannel> {
        val byId = channels.associateBy { it.id }

        // The channel's own rule for @everyone decides; without one, its category's does.
        fun hidden(channel: ListGuildChannels200ResponseInner): Boolean {
            val own = channel.permissionOverwrites?.firstOrNull { it.id == guildId }
            if (own != null) return own.deny.toLong() and VIEW_CHANNEL != 0L
            val category = channel.parentId?.let(byId::get) ?: return false
            return category.permissionOverwrites
                ?.firstOrNull { it.id == guildId }
                ?.let { it.deny.toLong() and VIEW_CHANNEL != 0L } ?: false
        }
        return channels
            .filter { it.type.value != CATEGORY && !hidden(it) }
            .sortedBy { it.position }
            .map { DiscordChannel(it.id, it.name) }
    }

    internal companion object {
        const val VIEW_CHANNEL = 1L shl 10
        const val CATEGORY = 4
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
        private val log = LoggerFactory.getLogger(DiscordChannelDirectory::class.java)
    }
}
