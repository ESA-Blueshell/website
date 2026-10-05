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
        val lacking =
            standing
                .read()
                .permissions
                .filterNot { it.granted }
                .map { it.name }
        if (lacking.isEmpty()) return emptyList()
        return listOf(
            RaisedAlert(
                key = "discord-bot-permissions:${lacking.joinToString(",")}",
                kind = AlertKind.DISCORD_BOT_PERMISSIONS,
                subjectId = null,
                subjectLabel = lacking.joinToString(", "),
                count = lacking.size.toLong(),
                since = null,
            ),
        )
    }
}
