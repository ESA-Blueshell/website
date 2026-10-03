package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.game.api.GameArchiveChanged
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GameChannelArchiveTest {
    private val channels: DiscordChannelKeeper = mock()
    private val archive = GameChannelArchive(channels)

    @Test
    fun `an archived game's channels go into the archive, a restored one's come back, and nothing moves without a bot`() {
        whenever(channels.available()).thenReturn(true)
        archive.onGameArchiveChanged(GameArchiveChanged("VAL", true, listOf("1")))
        archive.onGameArchiveChanged(GameArchiveChanged("VAL", false, listOf("1")))
        archive.onGameArchiveChanged(GameArchiveChanged("VAL", true, emptyList()))
        verify(channels).archive(listOf("1"))
        verify(channels).restore(listOf("1"))

        whenever(channels.available()).thenReturn(false)
        archive.onGameArchiveChanged(GameArchiveChanged("VAL", true, listOf("2")))
        verify(channels, never()).archive(listOf("2"))
        verify(channels, org.mockito.kotlin.times(1)).archive(any())
    }
}
