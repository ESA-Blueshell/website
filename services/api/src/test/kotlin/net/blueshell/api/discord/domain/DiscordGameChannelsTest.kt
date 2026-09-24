package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider

class DiscordGameChannelsTest {
    private val rooms =
        object : DoorSource {
            override fun textRooms() =
                listOf(
                    TextRoom("1", "324", "valorant", "Games"),
                    TextRoom("2", "324", "welcome", "Lobby"),
                    TextRoom("3", "324", "chess", "games"),
                    TextRoom("4", "324", "rules"),
                )

            override fun invite(channelId: String): String? = null
        }

    @Test
    fun `offers the channels filed under the games category, whatever its case`() {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn rooms }

        assertThat(DiscordGameChannels(provider, "Games").offered()!!.map { it.name }).containsExactly("valorant", "chess")
    }

    @Test
    fun `offers nothing without a bot`() {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn null }

        assertThat(DiscordGameChannels(provider, "Games").offered()).isNull()
    }
}
