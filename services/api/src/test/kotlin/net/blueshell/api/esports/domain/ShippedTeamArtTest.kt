package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.shippedPicturesOf
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ShippedTeamArtTest {
    private val poster = Entities.file()
    private val drawn = Entities.file()
    private val nomads = Entities.team(id = 1L)
    private val bareAlpha = Entities.teamSeason(game = "ALPHA")
    private val drawnAlpha = Entities.teamSeason(game = "ALPHA").also { it.banner = drawn }
    private val bareBeta = Entities.teamSeason(game = "BETA")
    private val teams = mock<TeamRepository> { on { findByNameIgnoreCase("Nomads") } doReturn nomads }
    private val fielded = mock<TeamSeasonRepository> { on { findAllByTeamId(1L) } doReturn listOf(bareAlpha, drawnAlpha, bareBeta) }

    private fun art(vararg rows: Pair<String, String>) =
        ShippedTeamArt(
            shippedPicturesOf(mapOf("teams.csv" to rows.map { (name, game) -> mapOf("name" to name, "game" to game) })) { poster },
            teams,
            fielded,
        )

    @Test
    fun `gives every season a team played a game without art the poster the seed names`() {
        val placed = art("Nomads" to "ALPHA").apply()

        assertThat(placed).isEqualTo(1)
        assertThat(bareAlpha.banner).isSameAs(poster)
        verify(fielded).save(bareAlpha)
        assertThat(drawnAlpha.banner).isSameAs(drawn)
        assertThat(bareBeta.banner).isNull()
    }

    @Test
    fun `skips a team the database no longer has, and a game the team never played`() {
        val art = art("Ghosts" to "ALPHA", "Nomads" to "GAMMA")

        art.onReady()

        assertThat(art.apply()).isZero()
    }
}
