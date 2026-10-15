package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DiscordReachAlertsTest {
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val alerts = DiscordReachAlerts(targets, targetIds, roles, channels)

    private val sitecie = Entities.target(id = 1, system = "DISCORD", externalId = "900")
    private val lancie = Entities.target(id = 2, system = "DISCORD", externalId = "901")
    private val unmade = Entities.target(id = 3, system = "DISCORD")

    @Test
    fun `tells the board of linked roles above the bot and channels opened to them it cannot keep`() {
        whenever(roles.available()).thenReturn(true)
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(listOf(sitecie, lancie, unmade))
        whenever(targetIds.find(sitecie)).thenReturn("900")
        whenever(targetIds.find(lancie)).thenReturn("901")
        whenever(roles.role("900")).thenReturn(KeptRole("900", "Sitecie", false))
        whenever(roles.role("901")).thenReturn(KeptRole("901", "Lancie", true))
        whenever(channels.beyondBot(setOf("900", "901"))).thenReturn(listOf(KeptChannel("8", "lan-room", KeptChannelKind.TEXT, null)))

        assertThat(alerts.audience).isEqualTo(AlertAudience.BOARD)
        assertThat(alerts.raised()).containsExactly(
            RaisedAlert("discord-roles-above-bot:Sitecie", AlertKind.DISCORD_ROLES_ABOVE_BOT, null, "Sitecie", 1, null, 0),
            RaisedAlert("discord-channels-beyond-bot:lan-room", AlertKind.DISCORD_CHANNELS_BEYOND_BOT, null, "lan-room", 1, null, 0),
        )
    }

    @Test
    fun `raises nothing without a bot, without a linked role, or with nothing in the way`() {
        assertThat(alerts.raised()).isEmpty()
        verifyNoInteractions(targets)

        whenever(roles.available()).thenReturn(true)
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(emptyList())
        assertThat(alerts.raised()).isEmpty()

        whenever(targets.findAllBySystem("DISCORD")).thenReturn(listOf(lancie))
        whenever(targetIds.find(lancie)).thenReturn("901")
        whenever(roles.role("901")).thenReturn(KeptRole("901", "Lancie", true))
        whenever(channels.beyondBot(setOf("901"))).thenReturn(emptyList())
        assertThat(alerts.raised()).isEmpty()
    }
}
