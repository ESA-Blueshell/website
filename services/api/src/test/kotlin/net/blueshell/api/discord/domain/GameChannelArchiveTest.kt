package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRefused
import net.blueshell.api.game.api.GameArchiveChanged
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GameChannelArchiveTest {
    private val channels: DiscordChannelKeeper = mock()
    private val archive = GameChannelArchive(channels)

    @Test
    fun `a game archived takes its channels into the archive, and brought back takes them out, never without a bot`() {
        whenever(channels.available()).thenReturn(true)

        archive.onGameArchiveChanged(GameArchiveChanged("CHESS", true, listOf("1")))
        archive.onGameArchiveChanged(GameArchiveChanged("CHESS", false, listOf("1")))
        archive.onGameArchiveChanged(GameArchiveChanged("GO", true, emptyList()))
        whenever(channels.available()).thenReturn(false)
        archive.onGameArchiveChanged(GameArchiveChanged("CHESS", true, listOf("2")))

        verify(channels).archive(listOf("1"))
        verify(channels).restore(listOf("1"))
        verify(channels, never()).archive(listOf("2"))
        verify(channels, never()).archive(emptyList())

        // Refused by Discord after the commit: said in the log, not thrown at the archive.
        whenever(channels.available()).thenReturn(true)
        whenever(channels.archive(listOf("5"))).thenThrow(DiscordRefused("no"))
        archive.onGameArchiveChanged(GameArchiveChanged("GO", true, listOf("5")))
    }
}
