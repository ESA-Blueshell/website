package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class DiscordLiveServiceTest {
    private val general =
        VoiceRoom("11", "General", position = 2, locked = false, people = listOf(VoicePerson("Emma", "https://cdn/emma.png")))
    private val lounge = VoiceRoom("12", "Members lounge", position = 1, locked = true, people = emptyList())
    private val server = VoiceServer(id = "324", name = "Blueshell Esports", rooms = listOf(general, lounge))

    private fun <T : Any> provided(bean: T?): ObjectProvider<T> = mock { on { ifAvailable } doReturn bean }

    private fun service(voice: VoiceServerSource?, counts: GuildCountsSource?) =
        DiscordLiveService(provided(voice), provided(counts), "https://discord.com/api/v10")

    @Test
    fun `puts the voice rooms in Discord's order with the counts and a way into each room`() {
        val live = service({ server }, { GuildCounts(members = 1199, online = 269) }).live()!!

        assertThat(live.server).isEqualTo("Blueshell Esports")
        assertThat(live.online).isEqualTo(269)
        assertThat(live.members).isEqualTo(1199)
        assertThat(live.rooms.map { it.room.name }).containsExactly("Members lounge", "General")
        assertThat(live.rooms.map { it.href }).containsExactly(
            "https://discord.com/channels/324/12",
            "https://discord.com/channels/324/11",
        )
    }

    @Test
    fun `leaves the counts out where Discord would not give them`() {
        val live = service({ server }, { null }).live()!!

        assertThat(live.online).isNull()
        assertThat(live.members).isNull()
        assertThat(service({ server }, null).live()!!.rooms).hasSize(2)
    }

    @Test
    fun `says nothing without a bot, or before the gateway has the server`() {
        assertThat(service(null, null).live()).isNull()
        assertThat(service({ null }, { GuildCounts(1, 1) }).live()).isNull()
    }
}
