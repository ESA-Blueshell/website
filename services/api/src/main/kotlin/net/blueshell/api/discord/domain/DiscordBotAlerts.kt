package net.blueshell.api.discord.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import org.springframework.stereotype.Component

/**
 * For the board: the bot is in the server and lacks a permission the site's work needs. Each
 * permission is its own alert under its own key, so hiding one never hides another that goes later.
 */
@Component
class DiscordBotAlerts(
    private val standing: BotStanding,
) : AlertSource {
    override val audience = AlertAudience.BOARD

    // Nothing is known of a bot that is not connected, so nothing is said of it.
    override fun raised(): List<RaisedAlert> =
        standing.read().permissions.filterNot { it.granted }.map {
            RaisedAlert(
                key = "discord-bot-permission:${it.name}",
                kind = AlertKind.DISCORD_BOT_PERMISSION,
                subjectId = null,
                subjectLabel = it.name,
                count = 1,
                since = null,
                mark = 0,
            )
        }
}
