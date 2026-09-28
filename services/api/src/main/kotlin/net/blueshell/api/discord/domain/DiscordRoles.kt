package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

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
    clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<List<ServerRole>>("Discord roles", KEPT_FOR, clock)

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
        return kept.get { client.listGuildRoles(guildId).map { ServerRole(it.id, it.name, it.position, it.managed, it.color) } }
    }

    internal companion object {
        val KEPT_FOR: Duration = Duration.ofMinutes(5)
    }
}
