package net.blueshell.api.discord.domain

import net.dv8tion.jda.api.Permission
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

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
)

/** Whether the bot may manage roles and channels, and which roles stay out of the site's hands. */
@Service
class BotStanding(
    private val gateway: ObjectProvider<GatewayGuild>,
    @Value($$"${discord.claim-roles:}") claimRoles: String,
) {
    private val claimRoleIds = claimRoles.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

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
        )
    }
}
