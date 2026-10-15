package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.ChannelOpening
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.game.api.GameChannelKind
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DiscordCatalogueTest {
    private val channels: DiscordChannelKeeper = mock()
    private val games: GameService = mock()
    private val catalogue = DiscordCatalogue(channels, games)

    @Test
    fun `lists every channel with who it is opened to and the game it is kept for, and nothing without a bot`() {
        assertThat(catalogue.channels()).isEmpty()
        verifyNoInteractions(games)

        whenever(channels.available()).thenReturn(true)
        val chess =
            Game(code = "CHESS", name = "Chess", slug = "chess").apply {
                channels += GameChannel("1", "g", "chess")
                esportsChannels += GameChannel("2", "g", "chess-team")
            }
        whenever(games.findAll()).thenReturn(listOf(chess))
        val casual = KeptChannel("1", "chess", KeptChannelKind.TEXT, "Games")
        val team = KeptChannel("2", "chess-team", KeptChannelKind.TEXT, "Esports")
        val sitecie = KeptChannel("3", "sitecie", KeptChannelKind.TEXT, "Committees")
        whenever(channels.openings()).thenReturn(
            listOf(
                ChannelOpening(casual, private = false, roleIds = emptyList()),
                ChannelOpening(team, private = true, roleIds = listOf("901")),
                ChannelOpening(sitecie, private = true, roleIds = listOf("900")),
            ),
        )

        val casualGame = ChannelGame("CHESS", "Chess", GameChannelKind.CASUAL)
        val teamGame = ChannelGame("CHESS", "Chess", GameChannelKind.COMPETITION)
        assertThat(catalogue.channels()).containsExactly(
            CataloguedChannel("1", "chess", KeptChannelKind.TEXT, "Games", false, emptyList(), casualGame),
            CataloguedChannel("2", "chess-team", KeptChannelKind.TEXT, "Esports", true, listOf("901"), teamGame),
            CataloguedChannel("3", "sitecie", KeptChannelKind.TEXT, "Committees", true, listOf("900"), null),
        )
    }
}
