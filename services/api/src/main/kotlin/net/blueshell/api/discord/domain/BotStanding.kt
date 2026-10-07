package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.discord.api.LinkedDiscordRoles
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/** One permission the site's work on Discord needs, by the name Discord's own settings give it. */
@Schema(name = "BotGrant")
data class BotGrant(
    val name: String,
    @field:Schema(description = "What the site cannot do without it.")
    val neededFor: String,
    val granted: Boolean,
)

/** Why the bot cannot keep who a channel is open to. */
@Schema(enumAsRef = true)
enum class BotChannelProblem {
    /** It cannot see the channel at all. */
    CANNOT_SEE,

    /** It sees the channel but may not change its permissions. */
    CANNOT_CHANGE_ACCESS,
}

/** A channel the site keeps that the bot cannot change, with what a link into it and a Discord-styled mark need. */
@Schema(name = "BotHiddenChannel")
data class BotHiddenChannel(
    val id: String,
    val guildId: String,
    val name: String,
    val category: String?,
    val voice: Boolean,
    val problem: BotChannelProblem = BotChannelProblem.CANNOT_SEE,
)

/**
 * What the bot may do in the server: manage roles and channels, and which roles it can hand out.
 * [connected] is false without a bot or before the gateway has the server, and then nothing else says
 * anything.
 */
data class BotStandingResult(
    val connected: Boolean,
    val manageRoles: Boolean,
    val manageChannels: Boolean,
    /** The role Discord made for the bot, or its highest where it has none; its permissions are set on it. */
    val botRole: DiscordRole?,
    /** Roles the site keeps that the bot cannot hand out, because they sit at or above its own. */
    val above: List<DiscordRole>,
    /** The roles the role-claim bot hands out, which the site never adopts. */
    val claimed: List<DiscordRole>,
    /** Every permission the site's work needs, and whether the bot holds it in the server. */
    val permissions: List<BotGrant> = emptyList(),
    /** The channels opened to a role the site keeps that the bot cannot see or change, and why. */
    val hidden: List<BotHiddenChannel> = emptyList(),
)

/** Whether the bot may manage roles and channels, and which roles stay out of the site's hands. */
@Service
class BotStanding(
    private val gateway: ObjectProvider<GatewayGuild>,
    private val settings: DiscordSettings,
    private val linked: LinkedDiscordRoles,
) {
    fun read(): BotStandingResult {
        val guild = gateway.ifAvailable?.guild() ?: return BotStandingResult(false, false, false, null, emptyList(), emptyList())
        val bot = guild.selfMember
        val claimRoleIds = settings.claimRoleIds()
        // Only what stands in the site's way: the roles it keeps, and the channels opened to them.
        val kept = linked.ids()
        val keepable = guild.roles.filterNot { it.isPublicRole || it.isManaged || it.id in claimRoleIds }
        return BotStandingResult(
            connected = true,
            manageRoles = bot.hasPermission(Permission.MANAGE_ROLES),
            manageChannels = bot.hasPermission(Permission.MANAGE_CHANNEL),
            botRole = (bot.roles.firstOrNull { it.isManaged } ?: bot.roles.firstOrNull())?.let(::describedRole),
            // A role the bot holds itself is never one it has to rise above.
            above = keepable.filter { it.id in kept }.filterNot { it in bot.roles || bot.canInteract(it) }.map(::describedRole),
            claimed = guild.roles.filter { it.id in claimRoleIds }.map(::describedRole),
            permissions = NEEDED.map { BotGrant(it.name, it.why, bot.hasPermission(it.permission)) },
            hidden =
                guild.channels
                    .filter { channel -> (channel as? IPermissionContainer)?.rolePermissionOverrides.orEmpty().any { it.id in kept } }
                    .mapNotNull { channel ->
                        when {
                            !bot.hasPermission(channel, Permission.VIEW_CHANNEL) -> BotChannelProblem.CANNOT_SEE
                            !bot.hasPermission(channel, Permission.MANAGE_PERMISSIONS) -> BotChannelProblem.CANNOT_CHANGE_ACCESS
                            else -> null
                        }?.let { hiddenChannel(channel, guild.id, it) }
                    },
        )
    }
}

private fun hiddenChannel(
    channel: GuildChannel,
    guildId: String,
    problem: BotChannelProblem,
) = BotHiddenChannel(
    id = channel.id,
    guildId = guildId,
    name = channel.name,
    category = (channel as? ICategorizableChannel)?.parentCategory?.name,
    voice = channel.type == ChannelType.VOICE,
    problem = problem,
)

/** A permission the site needs, under the name Discord's role settings give it, with what it is needed for. */
private data class Needed(
    val permission: Permission,
    val name: String,
    val why: String,
)

private val NEEDED =
    listOf(
        Needed(Permission.VIEW_CHANNEL, "View Channels", "Read the server's channels and who a channel is open to"),
        Needed(Permission.MANAGE_ROLES, "Manage Roles", "Make a role, add it to people or remove it, and set who a channel is open to"),
        Needed(Permission.MANAGE_CHANNEL, "Manage Channels", "Make a channel, archive it and remove it"),
        Needed(Permission.CREATE_INSTANT_INVITE, "Create Invite", "Make the invite a member joins the server with"),
        Needed(Permission.MESSAGE_SEND, "Send Messages", "Post an event that is announced"),
        Needed(Permission.MESSAGE_MENTION_EVERYONE, "Mention @everyone, @here, and All Roles", "Ping a role in an announcement"),
        Needed(Permission.MESSAGE_HISTORY, "Read Message History", "Read the posts with the most stars"),
    )
