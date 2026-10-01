package net.blueshell.api.discord.domain

import net.blueshell.api.discord.api.ChannelOpening
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.persistence.ChannelAccess
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
    private val policies: GameChannelPolicies = mock()
    private val catalogue = DiscordCatalogue(channels, games, policies)

    @Test
    fun `lists each channel with the roles it opens to, the game holding it and its kept access`() {
        val valorant =
            Game(code = "VALO", name = "Valorant", slug = "valorant").apply {
                channels.add(GameChannel("1", "99", "bs-valo"))
                esportsChannels.add(GameChannel("2", "99", "blueshell-valorant"))
            }
        val state =
            ChannelAccessState(
                AccessPolicy(ChannelAccess.READ, ChannelAccess.WRITE),
                AccessPolicy(ChannelAccess.WRITE, ChannelAccess.WRITE),
            )
        whenever(channels.available()).thenReturn(true)
        whenever(games.findAll()).thenReturn(listOf(valorant))
        whenever(policies.readKept()).thenReturn(mapOf("1" to state))
        whenever(channels.openings()).thenReturn(
            listOf(
                ChannelOpening(KeptChannel("1", "bs-valo", KeptChannelKind.TEXT, "Games"), private = false, roleIds = emptyList()),
                ChannelOpening(
                    KeptChannel("2", "blueshell-valorant", KeptChannelKind.TEXT, "Esports"),
                    private = true,
                    roleIds = listOf("7"),
                ),
                ChannelOpening(KeptChannel("3", "sitecie", KeptChannelKind.TEXT, "Committees"), private = true, roleIds = listOf("8")),
            ),
        )

        assertThat(catalogue.channels()).containsExactly(
            CataloguedChannel(
                "1",
                "bs-valo",
                KeptChannelKind.TEXT,
                "Games",
                false,
                emptyList(),
                ChannelGame("VALO", "Valorant", GameChannelKind.CASUAL),
                state,
            ),
            CataloguedChannel(
                "2",
                "blueshell-valorant",
                KeptChannelKind.TEXT,
                "Esports",
                true,
                listOf("7"),
                ChannelGame("VALO", "Valorant", GameChannelKind.COMPETITION),
                null,
            ),
            CataloguedChannel("3", "sitecie", KeptChannelKind.TEXT, "Committees", true, listOf("8"), null, null),
        )
    }

    @Test
    fun `lists nothing without a bot`() {
        assertThat(catalogue.channels()).isEmpty()
        verifyNoInteractions(games, policies)
    }
}
