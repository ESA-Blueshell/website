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

/** A role as a mention shows it: its name, and its colour where it has one. */
data class DiscordRoleName(
    val id: String,
    val name: String,
    val colour: Int?,
)

private data class ServerRole(
    val id: String,
    val name: String,
    val position: Int,
    val managed: Boolean,
    val colour: Int,
)

/**
 * The server's roles. [pingable] are those an event may ping, in the server's own order: everything
 * but @everyone, whose ID is the server's, and the roles an integration manages. Null without a bot,
 * or where Discord did not answer and nothing was kept.
 */
@Service
class DiscordRoleDirectory(
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
    internal var clock: Clock = Clock.systemUTC(),
) {
    @Volatile private var kept: Pair<Instant, List<ServerRole>>? = null

    fun pingable(): List<DiscordRole>? =
        roles()
            ?.filterNot { it.id == guildId || it.managed }
            ?.sortedByDescending { it.position }
            ?.map { DiscordRole(it.id, it.name) }

    /** Every role among [ids] the server has, @everyone and integration roles included. */
    fun named(ids: Set<String>): List<DiscordRoleName>? =
        roles()
            ?.filter { it.id in ids }
            ?.map { DiscordRoleName(it.id, it.name, it.colour.takeIf { colour -> colour != 0 }) }

    private fun roles(): List<ServerRole>? {
        val client = api.ifAvailable ?: return null
        val now = clock.instant()
        kept?.let { (at, roles) -> if (Duration.between(at, now) < KEPT_FOR) return roles }
        return runCatching { client.listGuildRoles(guildId) }
            .onFailure { log.warn("Discord roles could not be read", it) }
            .getOrNull()
            ?.map { ServerRole(it.id, it.name, it.position, it.managed, it.color) }
            ?.also { kept = now to it }
            ?: kept?.second
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
        private val log = LoggerFactory.getLogger(DiscordRoleDirectory::class.java)
    }
}
