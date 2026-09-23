package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class DiscordDoorServiceTest {
    private val fallback = "https://discord.gg/23YMFQy"
    private val rooms =
        listOf(
            TextRoom("481", "324", "👋welcome"),
            TextRoom("633", "324", "board-questions"),
        )

    private fun source(
        rooms: List<TextRoom>,
        invite: (String) -> String? = { "https://discord.gg/made-for-$it" },
    ) = object : DoorSource {
        override fun textRooms() = rooms

        override fun invite(channelId: String) = invite(channelId)
    }

    private fun service(source: DoorSource?): DiscordDoorService {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn source }
        return DiscordDoorService(provider, DiscordDoorsProperties(), "https://discord.com/api/v10")
    }

    @Test
    fun `leads each door to an invite the bot made into its channel, however the server decorates the name`() {
        val doors = service(source(rooms))

        assertThat(doors.invite(DiscordDoor.WELCOME)).isEqualTo("https://discord.gg/made-for-481")
        assertThat(doors.invite(DiscordDoor.BOARD)).isEqualTo("https://discord.gg/made-for-633")
    }

    @Test
    fun `leads a member to the channel itself`() {
        assertThat(service(source(rooms)).channel(DiscordDoor.BOARD)).isEqualTo("https://discord.com/channels/324/633")
    }

    @Test
    fun `falls back where the channel is missing, the bot may not invite, or there is no bot`() {
        assertThat(service(source(rooms)).invite(DiscordDoor.SUGGESTIONS)).isEqualTo(fallback)
        assertThat(service(source(rooms)).channel(DiscordDoor.SUGGESTIONS)).isEqualTo(fallback)
        assertThat(service(source(rooms) { null }).invite(DiscordDoor.WELCOME)).isEqualTo(fallback)
        assertThat(service(source(emptyList())).invite(DiscordDoor.WELCOME)).isEqualTo(fallback)
        assertThat(service(null).channel(DiscordDoor.WELCOME)).isEqualTo(fallback)
    }

    @Test
    fun `keeps falling back quietly after the first miss`() {
        val doors = service(source(rooms))

        repeat(2) { assertThat(doors.invite(DiscordDoor.SUGGESTIONS)).isEqualTo(fallback) }
    }

    @Test
    fun `knows each door by its key, and nothing else`() {
        assertThat(DiscordDoor.entries.map { DiscordDoor.of(it.key) }).containsExactlyElementsOf(DiscordDoor.entries)
        assertThat(DiscordDoor.of("WELCOME")).isNull()
        assertThat(DiscordDoorsProperties().channelOf(DiscordDoor.SUGGESTIONS)).isEqualTo("sitecie-suggestions")
        assertThat(plain("💡 Sitecie_Suggestions")).isEqualTo("sitecieSuggestions".lowercase())
    }
}
