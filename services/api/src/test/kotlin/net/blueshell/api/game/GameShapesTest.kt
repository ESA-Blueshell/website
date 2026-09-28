package net.blueshell.api.game

import net.blueshell.api.game.web.CasualGameResponse
import net.blueshell.api.game.web.GameChannelResponse
import net.blueshell.api.game.web.GameHoldingsResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The shapes the game routes answer with, read field by field. */
class GameShapesTest {
    @Test
    fun `answers every field it carries`() {
        val holdings = GameHoldingsResponse(channels = 1, committees = 2, events = 3, teams = 4, players = 5)
        val channel = GameChannelResponse(id = "900", guildId = "324", name = "chess")
        val game =
            CasualGameResponse(
                code = "CHESS",
                name = "Chess",
                slug = "chess",
                accent = "#b58863",
                intro = "Blitz",
                banner = null,
                icon = null,
                sortIndex = 4,
                archived = false,
                inCompetition = false,
                channels = listOf(channel),
            )

        assertThat(listOf(holdings.channels, holdings.committees, holdings.events, holdings.teams, holdings.players))
            .containsExactly(1L, 2L, 3L, 4L, 5L)
        assertThat(listOf(game.name, game.slug, game.accent, game.intro, game.banner, game.icon))
            .containsExactly("Chess", "chess", "#b58863", "Blitz", null, null)
        assertThat(game.sortIndex).isEqualTo(4)
        assertThat(channel.guildId).isEqualTo("324")
    }
}
