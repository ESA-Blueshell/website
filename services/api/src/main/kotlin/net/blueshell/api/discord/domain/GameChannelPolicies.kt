package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.discord.persistence.ChannelAccess
import net.blueshell.api.discord.persistence.ChannelPolicy
import net.blueshell.api.discord.persistence.ChannelPolicyRepository
import net.blueshell.api.shared.refusal.Refusal
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/** Who gets how far into a channel: everybody in the server, and members on top of that. */
@Schema(name = "ChannelAccessPolicy")
data class AccessPolicy(
    val everyone: ChannelAccess,
    val members: ChannelAccess,
) {
    companion object {
        /** A new games channel: everybody reads it, members write in it. */
        val DEFAULT = AccessPolicy(ChannelAccess.READ, ChannelAccess.WRITE)
    }
}

/** A channel's access as the site keeps it and as Discord has it now; [differs] is where they part. */
data class ChannelAccessState(
    val kept: AccessPolicy?,
    val actual: AccessPolicy,
) {
    val differs: Boolean get() = kept != null && kept != actual
}

/** A channel the site made, as a game's channel list keeps it. */
data class MadeChannel(
    val id: String,
    val guildId: String,
    val name: String,
)

class DiscordUnreachable : Refusal(HttpStatus.SERVICE_UNAVAILABLE, "DiscordUnreachable", "Discord cannot be reached now.")

/**
 * A game's channels and their access. A channel the site makes goes under the games or esports
 * category with the default policy; the policy the site keeps is written to Discord when the board
 * changes it, and only then, so a change made on Discord reads as a difference and is never undone.
 */
@Service
class GameChannelPolicies(
    private val gateway: ObjectProvider<GatewayGuild>,
    private val policies: ChannelPolicyRepository,
    @param:Value($$"${discord.games-category:Games}") private val gamesCategory: String,
    @param:Value($$"${discord.esports-category:Esports}") private val esportsCategory: String,
    @param:Value($$"${discord.cohort-roles.CURRENT_MEMBERS:}") private val memberRoleId: String,
) {
    fun create(
        name: String,
        category: GameChannelCategory,
    ): MadeChannel {
        val guild = guild()
        val named = if (category == GameChannelCategory.ESPORTS) esportsCategory else gamesCategory
        val parent = guild.getCategoriesByName(named, true).firstOrNull() ?: guild.createCategory(named).complete()
        val channel = parent.createTextChannel(name.trim()).complete()
        write(guild, channel, AccessPolicy.DEFAULT)
        policies.save(ChannelPolicy(channel.id, AccessPolicy.DEFAULT.everyone, AccessPolicy.DEFAULT.members))
        return MadeChannel(channel.id, guild.id, channel.name)
    }

    fun read(channelId: String): ChannelAccessState {
        val guild = guild()
        val channel = channelOf(guild, channelId)
        val kept = policies.findById(channelId).orElse(null)?.let { AccessPolicy(it.everyone, it.members) }
        return ChannelAccessState(kept, actualOf(guild, channel))
    }

    fun set(
        channelId: String,
        policy: AccessPolicy,
    ): ChannelAccessState {
        val guild = guild()
        write(guild, channelOf(guild, channelId), policy)
        val kept = policies.findById(channelId).orElse(null)
        if (kept == null) {
            policies.save(ChannelPolicy(channelId, policy.everyone, policy.members))
        } else {
            kept.everyone = policy.everyone
            kept.members = policy.members
            policies.save(kept)
        }
        return read(channelId)
    }

    private fun write(
        guild: Guild,
        channel: GuildChannel,
        policy: AccessPolicy,
    ) {
        val container = channel as IPermissionContainer
        overwrite(container, guild.publicRole, policy.everyone)
        memberRole(guild)?.let { overwrite(container, it, maxOf(policy.members, policy.everyone)) }
    }

    private fun overwrite(
        container: IPermissionContainer,
        role: Role,
        access: ChannelAccess,
    ) {
        val (allow, deny) =
            when (access) {
                ChannelAccess.HIDDEN -> emptyList<Permission>() to listOf(Permission.VIEW_CHANNEL)
                ChannelAccess.READ -> listOf(Permission.VIEW_CHANNEL) to listOf(Permission.MESSAGE_SEND)
                ChannelAccess.WRITE -> listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) to emptyList()
            }
        container
            .upsertPermissionOverride(role)
            .setAllowed(allow)
            .setDenied(deny)
            .complete()
    }

    private fun actualOf(
        guild: Guild,
        channel: GuildChannel,
    ): AccessPolicy {
        val everyone = accessOf(guild.publicRole, channel)
        val members = memberRole(guild)?.let { maxOf(accessOf(it, channel), everyone) } ?: everyone
        return AccessPolicy(everyone, members)
    }

    private fun accessOf(
        role: Role,
        channel: GuildChannel,
    ): ChannelAccess =
        when {
            !role.hasPermission(channel, Permission.VIEW_CHANNEL) -> ChannelAccess.HIDDEN
            !role.hasPermission(channel, Permission.MESSAGE_SEND) -> ChannelAccess.READ
            else -> ChannelAccess.WRITE
        }

    private fun memberRole(guild: Guild): Role? = memberRoleId.takeIf { it.isNotBlank() }?.let(guild::getRoleById)

    private fun guild(): Guild = gateway.ifAvailable?.guild() ?: throw DiscordUnreachable()

    private fun channelOf(
        guild: Guild,
        channelId: String,
    ): GuildChannel =
        guild.getGuildChannelById(channelId)?.takeIf { it is IPermissionContainer }
            ?: throw org.springframework.web.server
                .ResponseStatusException(HttpStatus.NOT_FOUND, "Discord has no channel $channelId")
}
