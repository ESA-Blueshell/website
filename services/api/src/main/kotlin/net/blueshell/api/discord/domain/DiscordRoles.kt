package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** A role in the server an event may ping. */
data class DiscordRole(
    val id: String,
    val name: String,
)

/**
 * The roles an event may ping, in the server's own order: everything but @everyone, whose ID is the
 * server's, and the roles an integration manages. Null without a bot, or where Discord did not
 * answer and nothing was kept.
 */
@Service
class DiscordRoleDirectory(
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
    internal var clock: Clock = Clock.systemUTC(),
) {
    @Volatile private var kept: Pair<Instant, List<DiscordRole>>? = null

    fun pingable(): List<DiscordRole>? {
        val client = api.ifAvailable ?: return null
        val now = clock.instant()
        kept?.let { (at, roles) -> if (Duration.between(at, now) < KEPT_FOR) return roles }
        return runCatching { client.listGuildRoles(guildId) }
            .onFailure { log.warn("Discord roles could not be read", it) }
            .getOrNull()
            ?.filterNot { it.id == guildId || it.managed }
            ?.sortedByDescending { it.position }
            ?.map { DiscordRole(it.id, it.name) }
            ?.also { kept = now to it }
            ?: kept?.second
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
        private val log = LoggerFactory.getLogger(DiscordRoleDirectory::class.java)
    }
}
