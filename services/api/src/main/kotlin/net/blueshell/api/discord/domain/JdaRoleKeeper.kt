package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordRefused
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.discord.api.RoleHolder
import net.blueshell.clients.discord.api.DiscordApi
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.UserSnowflake
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * The roles the site keeps, written through the gateway and their holders read over REST, which
 * pages the whole member list. Without a bot, or before the gateway has the server, it is not
 * [available] and every other call refuses with [DiscordUnavailable].
 */
@Service
class JdaRoleKeeper(
    private val gateway: ObjectProvider<GatewayGuild>,
    private val api: ObjectProvider<DiscordApi>,
    @Value($$"${discord.guildId:}") private val guildId: String,
    private val settings: DiscordSettings,
) : DiscordRoleKeeper {
    override fun available(): Boolean = gateway.ifAvailable?.guild() != null && api.ifAvailable != null

    override fun roles(): List<KeptRole> {
        val guild = guild()
        val claimed = settings.claimRoleIds()
        return guild.roles.filter { keepable(it, claimed) }.map { kept(guild, it) }
    }

    override fun role(id: String): KeptRole? {
        val guild = guild()
        val claimed = settings.claimRoleIds()
        return guild.getRoleById(id)?.takeIf { keepable(it, claimed) }?.let { kept(guild, it) }
    }

    override fun holders(roleId: String): List<RoleHolder> {
        val client = api.ifAvailable ?: throw DiscordUnavailable("There is no Discord bot.")
        val holders = mutableListOf<RoleHolder>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val page = client.listGuildMembers(guildId, PAGE, after)
            page.filter { roleId in it.roles }.mapTo(holders) { RoleHolder(it.user.id, it.nick ?: it.user.globalName ?: it.user.username) }
            if (page.size < PAGE) return holders
            after = page.last().user.id
        }
        // A cut-off list would read as members who lost the role.
        throw DiscordUnavailable("The server has more than ${PAGE * MAX_PAGES} members, more than the site reads.")
    }

    override fun add(
        roleId: String,
        discordUserId: String,
    ) {
        val guild = guild()
        val role = roleOf(guild, roleId)
        // A cohort's people are whoever the site's data says, so they never gain powers over the server through it.
        if (role.permissionsRaw and POWERS != 0L) {
            throw DiscordRefused("The site does not hand out ${role.name}: it has powers over the server.")
        }
        guild.addRoleToMember(UserSnowflake.fromId(discordUserId), role).complete()
    }

    override fun remove(
        roleId: String,
        discordUserId: String,
    ) {
        val guild = guild()
        guild.removeRoleFromMember(UserSnowflake.fromId(discordUserId), roleOf(guild, roleId)).complete()
    }

    override fun create(name: String): KeptRole {
        val guild = guild()
        return kept(
            guild,
            guild
                .createRole()
                .setName(name)
                .setMentionable(false)
                .complete(),
        )
    }

    override fun rename(
        roleId: String,
        name: String,
    ): KeptRole {
        val guild = guild()
        val role = roleOf(guild, roleId)
        role.manager.setName(name).complete()
        return KeptRole(role.id, name, guild.selfMember.canInteract(role))
    }

    override fun delete(roleId: String) {
        guild().getRoleById(roleId)?.delete()?.complete()
    }

    private fun guild(): Guild = gateway.ifAvailable?.guild() ?: throw DiscordUnavailable("The bot is not in the server right now.")

    private fun roleOf(
        guild: Guild,
        roleId: String,
    ): Role = guild.getRoleById(roleId) ?: throw DiscordUnavailable("Discord has no role $roleId.")

    private fun keepable(
        role: Role,
        claimed: Set<String>,
    ) = !role.isPublicRole && !role.isManaged && role.id !in claimed && role.permissionsRaw and POWERS == 0L

    private fun kept(
        guild: Guild,
        role: Role,
    ) = KeptRole(role.id, role.name, guild.selfMember.canInteract(role))

    private companion object {
        const val PAGE = 1000
        const val MAX_PAGES = 20
    }
}

// What lets a role's holders run the server rather than take part in it.
private val POWERS =
    Permission.getRaw(
        Permission.ADMINISTRATOR,
        Permission.MANAGE_SERVER,
        Permission.MANAGE_ROLES,
        Permission.MANAGE_CHANNEL,
        Permission.MANAGE_WEBHOOKS,
        Permission.BAN_MEMBERS,
        Permission.KICK_MEMBERS,
        Permission.MODERATE_MEMBERS,
    )
