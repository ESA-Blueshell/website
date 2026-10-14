package net.blueshell.api.discord.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import org.springframework.stereotype.Component

/**
 * For the board: the bot is in the server and lacks a permission the site's work needs. The alert
 * names the permissions, and its key changes with them, so hiding it for one does not hide another.
 */
@Component
class DiscordBotAlerts(
    private val standing: BotStanding,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    override fun raised(): List<RaisedAlert> {
        // Nothing is known of a bot that is not connected, so nothing is said of it.
        val permissions = standing.read().permissions
        val lacking = permissions.filterNot { it.granted }.map { it.name }
        if (lacking.isEmpty()) return emptyList()
        // One bit per permission in the check's own order, so a newly lacking one raises the mark.
        val mark = permissions.withIndex().filterNot { it.value.granted }.sumOf { 1L shl it.index }
        return listOf(
            RaisedAlert(
                key = "discord-bot-permissions",
                kind = AlertKind.DISCORD_BOT_PERMISSIONS,
                subjectId = null,
                subjectLabel = lacking.joinToString(", "),
                count = lacking.size.toLong(),
                since = null,
                mark = mark,
            ),
        )
    }
}
