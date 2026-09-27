package net.blueshell.api.game

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.game.web.CasualGameRequest
import net.blueshell.api.game.web.CasualGameResponse
import net.blueshell.api.game.web.GameChannelResponse
import net.blueshell.api.game.web.GameHoldingsResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.NamedInterface
import net.blueshell.api.game.api.PackageMetadata as ApiMetadata
import net.blueshell.api.game.persistence.PackageMetadata as EntitiesMetadata

/** The game module's declarations and the shapes its routes answer with, read field by field. */
class GameShapesTest {
    @Test
    fun `declares the module and the two surfaces it publishes`() {
        assertThat(ModuleMetadata()::class.java.getAnnotation(ApplicationModule::class.java).id).isEqualTo("game")
        assertThat(ApiMetadata()::class.java.getAnnotation(NamedInterface::class.java).value).containsExactly("api")
        assertThat(EntitiesMetadata()::class.java.getAnnotation(NamedInterface::class.java).value).containsExactly("entities")
    }

    @Test
    fun `a request left without channels keeps the ones the game has`() {
        val request = CasualGameRequest(name = "Chess", slug = "chess")

        assertThat(request.channels).isNull()
        assertThat(request.esportsChannels).isNull()
        assertThat(request.competitionIntro).isNull()
        assertThat(request.icon).isNull()
    }

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

    @Test
    fun `an embeddable channel and an entity can be made empty, as the persistence layer makes them`() {
        assertThat(GameChannel().channelId).isEmpty()
        // The no-argument constructor Hibernate calls runs no initialisers, so only its existence is read.
        assertThat(Game::class.java.getDeclaredConstructor().newInstance()).isInstanceOf(Game::class.java)
    }
}
