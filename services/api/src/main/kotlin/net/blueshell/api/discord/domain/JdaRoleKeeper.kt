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
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.PermissionException
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
        val role = keptRoleOf(guild, roleId)
        refusedByDiscord { guild.addRoleToMember(UserSnowflake.fromId(discordUserId), role).complete() }
    }

    override fun remove(
        roleId: String,
        discordUserId: String,
    ) {
        val guild = guild()
        val role = keptRoleOf(guild, roleId)
        refusedByDiscord { guild.removeRoleFromMember(UserSnowflake.fromId(discordUserId), role).complete() }
    }

    override fun create(name: String): KeptRole {
        val guild = guild()
        val made =
            refusedByDiscord {
                guild
                    .createRole()
                    .setName(name)
                    .setMentionable(false)
                    .complete()
            }
        return kept(guild, made)
    }

    override fun rename(
        roleId: String,
        name: String,
    ): KeptRole {
        val guild = guild()
        val role = roleOf(guild, roleId)
        refusedByDiscord { role.manager.setName(name).complete() }
        return KeptRole(role.id, name, guild.selfMember.canInteract(role))
    }

    override fun delete(roleId: String) {
        refusedByDiscord { guild().getRoleById(roleId)?.delete()?.complete() }
    }

    private fun guild(): Guild = gateway.ifAvailable?.guild() ?: throw DiscordUnavailable("The bot is not in the server right now.")

    private fun roleOf(
        guild: Guild,
        roleId: String,
    ): Role = guild.getRoleById(roleId) ?: throw DiscordUnavailable("Discord has no role $roleId.")

    // A cohort's people are whoever the site's data says, so the site only hands out and takes back
    // roles it keeps: never one with powers over the server, nor the claim bot's.
    private fun keptRoleOf(
        guild: Guild,
        roleId: String,
    ): Role {
        val role = roleOf(guild, roleId)
        if (!keepable(role, settings.claimRoleIds())) throw DiscordRefused("The site does not hand out or take back ${role.name}.")
        return role
    }

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
        Permission.MESSAGE_MANAGE,
        Permission.MESSAGE_MENTION_EVERYONE,
        Permission.MANAGE_THREADS,
        Permission.NICKNAME_MANAGE,
        Permission.VOICE_MUTE_OTHERS,
        Permission.VOICE_DEAF_OTHERS,
        Permission.VOICE_MOVE_OTHERS,
        Permission.MANAGE_EVENTS,
        Permission.MANAGE_GUILD_EXPRESSIONS,
        Permission.VIEW_AUDIT_LOGS,
    )

/** Answers the change, or a refusal with Discord's reason where Discord would not let the bot make it. */
internal fun <T> refusedByDiscord(change: () -> T): T =
    try {
        change()
    } catch (e: PermissionException) {
        throw DiscordRefused(e.message ?: "The bot may not make that change.").apply { initCause(e) }
    } catch (e: ErrorResponseException) {
        throw DiscordRefused(e.meaning).apply { initCause(e) }
    }
