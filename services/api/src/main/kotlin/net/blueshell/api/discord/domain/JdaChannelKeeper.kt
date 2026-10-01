package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.ChannelOpening
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.persistence.ArchivedChannel
import net.blueshell.api.discord.persistence.ArchivedChannelRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** The server's channels opened to roles through the gateway, by permission overwrites. */
@Service
class JdaChannelKeeper(
    private val gateway: ObjectProvider<GatewayGuild>,
    private val archived: ArchivedChannelRepository,
    @param:Value($$"${discord.archive-category:Archive}") private val archiveCategory: String,
) : DiscordChannelKeeper {
    override fun available(): Boolean = gateway.ifAvailable?.guild() != null

    override fun channels(): List<KeptChannel> = guild().channels.mapNotNull(::kept)

    override fun openings(): List<ChannelOpening> {
        val guild = guild()
        val everyone = guild.publicRole
        return guild.channels.mapNotNull { channel ->
            val kept = kept(channel) ?: return@mapNotNull null
            val opened =
                (channel as? IPermissionContainer)
                    ?.rolePermissionOverrides
                    .orEmpty()
                    .filter { Permission.VIEW_CHANNEL in it.allowed && it.id != everyone.id }
                    .map { it.id }
            ChannelOpening(kept, private = !everyone.hasPermission(channel, Permission.VIEW_CHANNEL), roleIds = opened)
        }
    }

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

    override fun archive(channelIds: Collection<String>) {
        val guild = guild()
        val archive = guild.getCategoriesByName(archiveCategory, true).firstOrNull() ?: guild.createCategory(archiveCategory).complete()
        channelIds.mapNotNull { guild.getGuildChannelById(it) as? ICategorizableChannel }.forEach { channel ->
            if (channel.parentCategoryIdLong == archive.idLong) return@forEach
            archived.save(ArchivedChannel(channel.id, channel.parentCategoryId))
            channel.manager.setParent(archive).complete()
        }
    }

    override fun restore(channelIds: Collection<String>) {
        val guild = guild()
        channelIds.forEach { id ->
            val kept = archived.findById(id).orElse(null) ?: return@forEach
            val channel = guild.getGuildChannelById(id) as? ICategorizableChannel
            channel?.manager?.setParent(kept.categoryId?.let(guild::getCategoryById))?.complete()
            archived.delete(kept)
        }
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
