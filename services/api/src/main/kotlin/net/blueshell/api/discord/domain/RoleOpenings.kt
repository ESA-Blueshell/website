package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.persistence.RoleAccess
import net.blueshell.api.discord.persistence.RoleOpening
import net.blueshell.api.discord.persistence.RoleOpeningKey
import net.blueshell.api.discord.persistence.RoleOpeningRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** A channel or category a role opens, at the access the site keeps and the access Discord has; [differs] is where they part. */
data class RoleOpeningState(
    val channel: KeptChannel,
    val kept: RoleAccess?,
    val actual: RoleAccess?,
) {
    val differs: Boolean get() = kept != actual
}

/**
 * What a role opens: categories and channels, each at an access. The board's change is written to
 * Discord as the role's overwrite and kept, and only then; a change made on Discord reads as a
 * difference and is never undone. Removing one takes the role's overwrite away and deletes nothing.
 */
@Service
class RoleOpenings(
    private val gateway: ObjectProvider<GatewayGuild>,
    private val kept: RoleOpeningRepository,
    private val channels: DiscordChannelKeeper,
) {
    @Transactional(readOnly = true)
    fun read(roleId: String): List<RoleOpeningState> {
        val guild = guild()
        val role = roleOf(guild, roleId)
        val keptBy = kept.findAllByKeyRoleId(roleId).associate { it.key.channelId to it.access }
        return guild.channels.mapNotNull { channel ->
            val listed = keptOf(channel) ?: return@mapNotNull null
            val actual = actualOf(channel, role)
            val keptAccess = keptBy[channel.id]
            if (actual == null && keptAccess == null) null else RoleOpeningState(listed, keptAccess, actual)
        }
    }

    @Transactional
    fun set(
        roleId: String,
        channelId: String,
        access: RoleAccess,
    ): List<RoleOpeningState> {
        val guild = guild()
        write(containerOf(guild, channelId), roleOf(guild, roleId), access)
        val key = RoleOpeningKey(roleId, channelId)
        val row = kept.findById(key).orElse(null)
        if (row == null) kept.save(RoleOpening(key, access)) else row.access = access
        return read(roleId)
    }

    @Transactional
    fun remove(
        roleId: String,
        channelId: String,
    ): List<RoleOpeningState> {
        val guild = guild()
        containerOf(guild, channelId).getPermissionOverride(roleOf(guild, roleId))?.delete()?.complete()
        kept.deleteById(RoleOpeningKey(roleId, channelId))
        return read(roleId)
    }

    /** Makes a text channel under [category], kept from everybody but the role, at [access]. */
    @Transactional
    fun create(
        roleId: String,
        name: String,
        category: String,
        access: RoleAccess,
    ): List<RoleOpeningState> {
        val made = reachable { channels.createPrivate(name.trim(), category, roleId) }
        return set(roleId, made.id, access)
    }

    /** Moves a channel into the archive category; it stays, read only, for its history. */
    fun archive(
        roleId: String,
        channelId: String,
    ): List<RoleOpeningState> {
        reachable { channels.archive(listOf(channelId)) }
        return read(roleId)
    }

    private fun write(
        container: IPermissionContainer,
        role: Role,
        access: RoleAccess,
    ) {
        val (allow, deny) =
            when (access) {
                RoleAccess.WRITE -> listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY) to emptyList()
                RoleAccess.READ -> listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY) to listOf(Permission.MESSAGE_SEND)
                RoleAccess.SPEAK -> listOf(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK) to emptyList()
            }
        container
            .upsertPermissionOverride(role)
            .setAllowed(allow)
            .setDenied(deny)
            .complete()
    }

    // Read off the role's own overwrite: what the board set is an overwrite, not what other roles add.
    private fun actualOf(
        channel: GuildChannel,
        role: Role,
    ): RoleAccess? {
        val allowed = (channel as? IPermissionContainer)?.getPermissionOverride(role)?.allowed ?: return null
        return when {
            Permission.VIEW_CHANNEL !in allowed -> null
            Permission.VOICE_CONNECT in allowed && Permission.VOICE_SPEAK in allowed -> RoleAccess.SPEAK
            Permission.MESSAGE_SEND in allowed -> RoleAccess.WRITE
            else -> RoleAccess.READ
        }
    }

    private fun <T> reachable(call: () -> T): T =
        try {
            call()
        } catch (e: DiscordUnavailable) {
            throw DiscordUnreachable().apply { initCause(e) }
        }

    private fun guild(): Guild = gateway.ifAvailable?.guild() ?: throw DiscordUnreachable()

    private fun roleOf(
        guild: Guild,
        roleId: String,
    ): Role = guild.getRoleById(roleId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Discord has no role $roleId")

    private fun containerOf(
        guild: Guild,
        channelId: String,
    ): IPermissionContainer =
        guild.getGuildChannelById(channelId) as? IPermissionContainer
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Discord has no channel $channelId")
}
