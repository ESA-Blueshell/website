package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * For the board: what stops the site keeping the roles it links. A linked role at or above the bot's
 * own cannot be added to anybody, and a channel opened to a linked role that the bot cannot see or
 * set access on cannot be kept. Only this module knows which roles are linked, so it asks Discord.
 * Read from the gateway's own copy of the server, so asking on every look costs no call.
 */
@Component
class DiscordReachAlerts(
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> {
        if (!roles.available()) return emptyList()
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        if (linked.isEmpty()) return emptyList()
        val above =
            linked
                .mapNotNull(roles::role)
                .filterNot { it.assignable }
                .map { it.name }
                .sorted()
        val beyond = channels.beyondBot(linked).map { it.name }.sorted()
        return listOfNotNull(
            alertOf(AlertKind.DISCORD_ROLES_ABOVE_BOT, "discord-roles-above-bot", above),
            alertOf(AlertKind.DISCORD_CHANNELS_BEYOND_BOT, "discord-channels-beyond-bot", beyond),
        )
    }

    // Keyed by the names, so hiding the alert for these does not hide it for the next.
    private fun alertOf(
        kind: AlertKind,
        prefix: String,
        names: List<String>,
    ): RaisedAlert? =
        names.takeIf { it.isNotEmpty() }?.let {
            RaisedAlert(
                key = "$prefix:${it.joinToString(",")}",
                kind = kind,
                subjectId = null,
                subjectLabel = it.joinToString(", "),
                count = it.size.toLong(),
                since = null,
            )
        }
}
