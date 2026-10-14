package net.blueshell.api.discord.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordBotAlertsTest {
    private val standing: BotStanding = mock()
    private val alerts = DiscordBotAlerts(standing)

    private fun read(vararg permissions: BotGrant) =
        BotStandingResult(true, true, true, null, emptyList(), emptyList(), permissions.toList())

    @Test
    fun `raises one alert per lacking permission, keyed by it, so hiding one never hides another`() {
        whenever(standing.read()).thenReturn(
            read(
                BotGrant("View Channels", "Read the channels", true),
                BotGrant("Manage Roles", "Make a role", false),
                BotGrant("Create Invite", "Make the invite", false),
            ),
        )

        assertThat(alerts.audience).isEqualTo(AlertAudience.BOARD)
        assertThat(alerts.raised()).containsExactly(
            lacking("Manage Roles"),
            lacking("Create Invite"),
        )
    }

    private fun lacking(permission: String) =
        RaisedAlert(
            key = "discord-bot-permission:$permission",
            kind = AlertKind.DISCORD_BOT_PERMISSION,
            subjectId = null,
            subjectLabel = permission,
            count = 1,
            since = null,
            mark = 0,
        )

    @Test
    fun `says nothing while the bot holds every permission, or is not connected`() {
        whenever(standing.read()).thenReturn(read(BotGrant("View Channels", "Read the channels", true)))
        assertThat(alerts.raised()).isEmpty()

        whenever(standing.read()).thenReturn(BotStandingResult(false, false, false, null, emptyList(), emptyList()))
        assertThat(alerts.raised()).isEmpty()
    }
}
