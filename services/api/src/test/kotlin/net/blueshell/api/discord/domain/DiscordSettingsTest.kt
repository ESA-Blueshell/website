package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.DiscordSetting
import net.blueshell.api.discord.persistence.DiscordSettingName
import net.blueshell.api.discord.persistence.DiscordSettingRepository
import net.blueshell.api.discord.web.DiscordBotSettingsController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class DiscordSettingsTest {
    private val stored = mutableMapOf<DiscordSettingName, DiscordSetting>()
    private val repository: DiscordSettingRepository =
        mock {
            on { findById(any()) } doAnswer { Optional.ofNullable(stored[it.arguments[0] as DiscordSettingName]) }
            on { save(any<DiscordSetting>()) } doAnswer { (it.arguments[0] as DiscordSetting).also { row -> stored[row.name] = row } }
            on { deleteById(any()) } doAnswer {
                stored.remove(it.arguments[0] as DiscordSettingName)
                Unit
            }
        }
    private val settings = DiscordSettings(repository, "events-info", "events-calendar", "starboard", " 7, 8 ,")

    @Test
    fun `reads the configuration for every setting the board never set`() {
        assertThat(settings.read()).isEqualTo(DiscordBotSettings("events-info", "events-calendar", "starboard", listOf("7", "8")))
        assertThat(listOf(settings.info(), settings.calendar(), settings.starboard()))
            .containsExactly("events-info", "events-calendar", "starboard")
        assertThat(settings.claimRoleIds()).containsExactlyInAnyOrder("7", "8")
        assertThat(DiscordSetting(DiscordSettingName.INFO_CHANNEL, "news").id).isEqualTo(DiscordSettingName.INFO_CHANNEL)
        // The no-argument constructor JPA loads a row through.
        assertThat(DiscordSetting::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `keeps what the board sets, a channel without its hash, and goes back to the configuration for a blank channel`() {
        settings.write(DiscordBotSettings("#announcements ", "calendar", "", listOf("9", " 9", "")))

        assertThat(settings.read()).isEqualTo(DiscordBotSettings("announcements", "calendar", "starboard", listOf("9")))
        settings.write(DiscordBotSettings("announcements", "today", "", emptyList()))
        assertThat(settings.calendar()).isEqualTo("today")
        assertThat(settings.claimRoleIds()).isEmpty()
        verify(repository, times(2)).deleteById(DiscordSettingName.STARBOARD_CHANNEL)
        val saved = argumentCaptor<DiscordSetting>()
        verify(repository, org.mockito.kotlin.atLeastOnce()).save(saved.capture())
        assertThat(saved.allValues.map { it.name }).contains(DiscordSettingName.INFO_CHANNEL, DiscordSettingName.CLAIM_ROLES)
    }

    @Test
    fun `the settings page reads and writes them`() {
        val controller = DiscordBotSettingsController(settings)
        assertThat(controller.findDiscordBotSettings().infoChannel).isEqualTo("events-info")
        val news = DiscordSetting(DiscordSettingName.INFO_CHANNEL, "news")
        whenever(repository.findById(DiscordSettingName.INFO_CHANNEL)).thenReturn(Optional.of(news))
        val saved = controller.setDiscordBotSettings(DiscordBotSettings("news", "events-calendar", "starboard", emptyList()))
        assertThat(saved.infoChannel).isEqualTo("news")
    }
}
