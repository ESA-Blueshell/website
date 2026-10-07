package net.blueshell.api.cohort.domain

import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DiscordReachAlertsTest {
    private val targets =
        mock<TargetRepository> {
            on { findAllBySystem("DISCORD") } doReturn
                listOf(
                    Entities.target(id = 1L, cohortId = 1L, externalId = "900"),
                    Entities.target(id = 2L, cohortId = 2L, externalId = "901"),
                    Entities.target(id = 3L, cohortId = 3L, externalId = null),
                )
        }
    private val roles =
        mock<DiscordRoleKeeper> {
            on { available() } doReturn true
            on { role("900") } doReturn KeptRole("900", "Board", assignable = false)
            on { role("901") } doReturn KeptRole("901", "Sitecie", assignable = true)
        }
    private val channels = mock<DiscordChannelKeeper>()
    private val alerts = DiscordReachAlerts(targets, CohortTargetIds(targets), roles, channels)

    @Test
    fun `names the linked roles above the bot and the channels it cannot keep, one alert each`() {
        whenever(channels.beyondBot(setOf("900", "901"))).thenReturn(listOf(KeptChannel("1", "board-room", KeptChannelKind.TEXT, "Board")))

        val raised = alerts.raised()

        assertThat(raised.map { listOf(it.kind, it.subjectLabel, it.count, it.key) }).containsExactly(
            listOf(AlertKind.DISCORD_ROLES_ABOVE_BOT, "Board", 1L, "discord-roles-above-bot:Board"),
            listOf(AlertKind.DISCORD_CHANNELS_BEYOND_BOT, "board-room", 1L, "discord-channels-beyond-bot:board-room"),
        )
        assertThat(alerts.audience.name).isEqualTo("BOARD")
    }

    @Test
    fun `says nothing where all is within the bot's reach, nothing is linked or there is no bot`() {
        whenever(roles.role("900")).thenReturn(KeptRole("900", "Board", assignable = true))
        whenever(channels.beyondBot(any())).thenReturn(emptyList())
        assertThat(alerts.raised()).isEmpty()

        whenever(targets.findAllBySystem("DISCORD")).thenReturn(emptyList())
        assertThat(alerts.raised()).isEmpty()

        whenever(roles.available()).thenReturn(false)
        assertThat(alerts.raised()).isEmpty()
        verify(channels, never()).beyondBot(setOf())
    }
}
