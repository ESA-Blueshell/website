package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/** The server's channels opened to roles through the gateway, by permission overwrites. */
@Service
class JdaChannelKeeper(
    private val gateway: ObjectProvider<GatewayGuild>,
) : DiscordChannelKeeper {
    override fun available(): Boolean = gateway.ifAvailable?.guild() != null

    override fun channels(): List<KeptChannel> = guild().channels.mapNotNull(::kept)

    override fun openedTo(roleId: String): List<KeptChannel> {
        val guild = guild()
        val role = roleOf(guild, roleId)
        return guild.channels
            .filter { (it as? IPermissionContainer)?.getPermissionOverride(role)?.allowed?.contains(Permission.VIEW_CHANNEL) == true }
            .mapNotNull(::kept)
    }

    override fun createPrivate(
        name: String,
        category: String,
        roleId: String,
    ): KeptChannel {
        val guild = guild()
        val role = roleOf(guild, roleId)
        val parent = guild.getCategoriesByName(category, true).firstOrNull() ?: guild.createCategory(category).complete()
        val made =
            parent
                .createTextChannel(name)
                .addPermissionOverride(guild.publicRole, emptyList(), listOf(Permission.VIEW_CHANNEL))
                .addPermissionOverride(role, TEXT_ACCESS, emptyList())
                .complete()
        return requireNotNull(kept(made))
    }

    override fun open(
        channelId: String,
        roleId: String,
        private: Boolean,
    ) {
        val guild = guild()
        val channel = containerOf(guild, channelId)
        val access = if ((channel as GuildChannel).type == ChannelType.VOICE) VOICE_ACCESS else TEXT_ACCESS
        channel.upsertPermissionOverride(roleOf(guild, roleId)).grant(access).complete()
        if (private) channel.upsertPermissionOverride(guild.publicRole).deny(Permission.VIEW_CHANNEL).complete()
    }

    override fun close(
        channelId: String,
        roleId: String,
    ) {
        val guild = guild()
        containerOf(guild, channelId).getPermissionOverride(roleOf(guild, roleId))?.delete()?.complete()
    }

    override fun delete(channelId: String) {
        guild().getGuildChannelById(channelId)?.delete()?.complete()
    }

    private fun guild(): Guild = gateway.ifAvailable?.guild() ?: throw DiscordUnavailable("The bot is not in the server right now.")

    private fun roleOf(
        guild: Guild,
        roleId: String,
    ): Role = guild.getRoleById(roleId) ?: throw DiscordUnavailable("Discord has no role $roleId.")

    private fun containerOf(
        guild: Guild,
        channelId: String,
    ): IPermissionContainer =
        guild.getGuildChannelById(channelId) as? IPermissionContainer ?: throw DiscordUnavailable("Discord has no channel $channelId.")

    private fun kept(channel: GuildChannel): KeptChannel? {
        val kind =
            when (channel.type) {
                ChannelType.TEXT, ChannelType.NEWS, ChannelType.FORUM -> KeptChannelKind.TEXT
                ChannelType.VOICE, ChannelType.STAGE -> KeptChannelKind.VOICE
                ChannelType.CATEGORY -> KeptChannelKind.CATEGORY
                else -> return null
            }
        return KeptChannel(channel.id, channel.name, kind, (channel as? ICategorizableChannel)?.parentCategory?.name)
    }

    private companion object {
        val TEXT_ACCESS = listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
        val VOICE_ACCESS = listOf(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK)
    }
}
