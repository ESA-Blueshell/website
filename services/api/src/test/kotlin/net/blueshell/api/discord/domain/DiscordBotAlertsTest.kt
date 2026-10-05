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

    private fun read(vararg permissions: BotPermission) =
        BotStandingResult(true, true, true, null, emptyList(), emptyList(), permissions.toList())

    @Test
    fun `tells the board which permissions the bot lacks, under a key that changes with them`() {
        whenever(standing.read()).thenReturn(
            read(
                BotPermission("View Channels", "Read the channels", true),
                BotPermission("Manage Roles", "Make a role", false),
                BotPermission("Create Invite", "Make the invite", false),
            ),
        )

        assertThat(alerts.audience).isEqualTo(AlertAudience.BOARD)
        assertThat(alerts.raised()).containsExactly(
            RaisedAlert(
                key = "discord-bot-permissions:Manage Roles,Create Invite",
                kind = AlertKind.DISCORD_BOT_PERMISSIONS,
                subjectId = null,
                subjectLabel = "Manage Roles, Create Invite",
                count = 2,
                since = null,
            ),
        )
    }

    @Test
    fun `says nothing while the bot holds every permission, or is not connected`() {
        whenever(standing.read()).thenReturn(read(BotPermission("View Channels", "Read the channels", true)))
        assertThat(alerts.raised()).isEmpty()

        whenever(standing.read()).thenReturn(BotStandingResult(false, false, false, null, emptyList(), emptyList()))
        assertThat(alerts.raised()).isEmpty()
    }
}
