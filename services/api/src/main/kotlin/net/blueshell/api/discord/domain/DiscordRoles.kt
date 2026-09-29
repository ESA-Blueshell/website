package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.entities.Role
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** A role in the server as a mention shows it: its name, and its colour where it has one. */
data class DiscordRole(
    val id: String,
    val name: String,
    val colour: Int?,
)

/**
 * The server's roles, as the gateway holds them. [pingable] are those an event may ping, in the
 * server's own order: everything but @everyone and the roles an integration manages. Null without
 * a bot, or while the gateway has never had the server; the last roles it had serve while it is
 * away.
 */
@Service
class DiscordRoleDirectory(
    private val gateway: ObjectProvider<GatewayGuild>,
    clock: Clock = Clock.systemUTC(),
) {
    private val kept = KeptRead<List<Role>>("Discord roles", Duration.ZERO, clock)

    fun pingable(): List<DiscordRole>? =
        roles()
            ?.filterNot { it.isPublicRole || it.isManaged }
            ?.map(::described)

    /** Every role among [ids] the server has, @everyone and integration roles included. */
    fun named(ids: Set<String>): List<DiscordRole>? =
        roles()
            ?.filter { it.id in ids }
            ?.map(::described)

    private fun described(role: Role) = DiscordRole(role.id, role.name, role.colors.takeUnless { it.isDefault }?.primaryRaw)

    // Highest first, as the server lists them.
    private fun roles(): List<Role>? {
        val source = gateway.ifAvailable ?: return null
        return kept.get { source.guild()?.roles }
    }
}
