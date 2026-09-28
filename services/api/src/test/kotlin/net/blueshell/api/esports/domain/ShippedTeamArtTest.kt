package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.shippedPicturesOf
import net.blueshell.api.file.persistence.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ShippedTeamArtTest {
    private val poster = mock<File>()
    private val drawn = mock<File>()
    private val nomads = mock<Team> { on { id } doReturn 1L }
    private val bareAlpha = mock<TeamSeason> { on { game } doReturn "ALPHA" }
    private val drawnAlpha =
        mock<TeamSeason> {
            on { game } doReturn "ALPHA"
            on { banner } doReturn drawn
        }
    private val bareBeta = mock<TeamSeason> { on { game } doReturn "BETA" }
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
        verify(bareAlpha).banner = poster
        verify(fielded).save(bareAlpha)
        verify(drawnAlpha, never()).banner = poster
        verify(bareBeta, never()).banner = poster
    }

    @Test
    fun `skips a team the database no longer has, and a game the team never played`() {
        val art = art("Ghosts" to "ALPHA", "Nomads" to "GAMMA")

        art.onReady()

        assertThat(art.apply()).isZero()
    }
}
