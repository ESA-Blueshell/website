package net.blueshell.api.esports.web

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameResponseMappingTest {
    @Test
    fun `a game read on the competition pages carries their own intro and its esports channels`() {
        val valorant =
            Game(code = "VALORANT", name = "Valorant", slug = "valorant", intro = "Customs", competitionIntro = "Two teams").apply {
                channels += GameChannel("6", "324", "valorant")
                esportsChannels += GameChannel("7", "324", "valorant-esports")
            }

        val read = valorant.asResponse(current = true)

        assertThat(read.intro).isEqualTo("Customs")
        assertThat(read.competitionIntro).isEqualTo("Two teams")
        assertThat(read.esportsChannels).containsExactly(EsportsChannelResponse("7", "324", "valorant-esports"))
        assertThat(read.esportsChannels.single().let { listOf(it.id, it.guildId, it.name) }).containsExactly("7", "324", "valorant-esports")
        assertThat(Game(code = "GO", name = "Go", slug = "go").asResponse().esportsChannels).isEmpty()
    }
}
