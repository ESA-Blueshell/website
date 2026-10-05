package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.dv8tion.jda.api.Permission
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** One permission the site's work on Discord needs, by the name Discord's own settings give it. */
@Schema(name = "BotPermission")
data class BotPermission(
    val name: String,
    @field:Schema(description = "What the site cannot do without it.")
    val neededFor: String,
    val granted: Boolean,
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
    /** The bot's highest role; Discord lets it hand out only the roles below it. */
    val botRole: DiscordRole?,
    /** Roles the site could keep but the bot cannot hand out, because they sit at or above its own. */
    val above: List<DiscordRole>,
    /** The roles the role-claim bot hands out, which the site never adopts. */
    val claimed: List<DiscordRole>,
    /** Every permission the site's work needs, and whether the bot holds it in the server. */
    val permissions: List<BotPermission> = emptyList(),
    /** The channels the bot cannot see, which the site can neither read nor change. */
    val hidden: List<String> = emptyList(),
)

/** Whether the bot may manage roles and channels, and which roles stay out of the site's hands. */
@Service
class BotStanding(
    private val gateway: ObjectProvider<GatewayGuild>,
    @Value($$"${discord.claim-roles:}") claimRoles: String,
) {
    private val claimRoleIds =
        claimRoles
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun read(): BotStandingResult {
        val guild = gateway.ifAvailable?.guild() ?: return BotStandingResult(false, false, false, null, emptyList(), emptyList())
        val bot = guild.selfMember
        val keepable = guild.roles.filterNot { it.isPublicRole || it.isManaged || it.id in claimRoleIds }
        return BotStandingResult(
            connected = true,
            manageRoles = bot.hasPermission(Permission.MANAGE_ROLES),
            manageChannels = bot.hasPermission(Permission.MANAGE_CHANNEL),
            botRole = bot.roles.firstOrNull()?.let(::describedRole),
            above = keepable.filterNot(bot::canInteract).map(::describedRole),
            claimed = guild.roles.filter { it.id in claimRoleIds }.map(::describedRole),
            permissions = NEEDED.map { BotPermission(it.name, it.why, bot.hasPermission(it.permission)) },
            hidden = guild.channels.filterNot { bot.hasPermission(it, Permission.VIEW_CHANNEL) }.map { it.name },
        )
    }
}

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
