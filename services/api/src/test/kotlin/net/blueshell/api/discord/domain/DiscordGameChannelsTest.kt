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
                    TextRoom("5", "324", "valorant-esports", "Esports"),
                )

            override fun invite(channelId: String): String? = null
        }

    @Test
    fun `offers the channels filed under the games category, whatever its case`() {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn rooms }

        assertThat(channels(provider).offered(GameChannelCategory.GAMES)!!.map { it.name }).containsExactly("valorant", "chess")
    }

    @Test
    fun `offers the channels filed under the esports category apart from the games ones`() {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn rooms }

        assertThat(channels(provider).offered(GameChannelCategory.ESPORTS)!!.map { it.name }).containsExactly("valorant-esports")
    }

    @Test
    fun `offers nothing without a bot`() {
        val provider: ObjectProvider<DoorSource> = mock { on { ifAvailable } doReturn null }

        assertThat(channels(provider).offered(GameChannelCategory.ESPORTS)).isNull()
    }

    private fun channels(provider: ObjectProvider<DoorSource>) = DiscordGameChannels(provider, "Games", "Esports")
}
