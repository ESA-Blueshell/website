package net.blueshell.api.game.domain

import net.blueshell.api.file.api.shippedPicturesOf
import net.blueshell.api.file.persistence.File
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ShippedGameArtTest {
    private val picture = mock<File>()
    private val chosen = mock<File>()
    private val alpha = Game(code = "ALPHA", name = "Alpha", slug = "alpha")
    private val beta = Game(code = "BETA", name = "Beta", slug = "beta").apply { banner = chosen }
    private val games =
        mock<GameRepository> {
            on { findByCode("ALPHA") } doReturn alpha
            on { findByCode("BETA") } doReturn beta
        }
    private val pictures =
        shippedPicturesOf(
            mapOf(
                "banners.csv" to listOf(mapOf("game" to "ALPHA"), mapOf("game" to "BETA"), mapOf("game" to "GONE")),
                "icons.csv" to listOf(mapOf("game" to "ALPHA")),
            ),
        ) { picture }
    private val art = ShippedGameArt(pictures, games)

    @Test
    fun `gives a game the banner and icon the seed names where it has none`() {
        assertThat(art.apply()).isEqualTo(ShippedGameArt.Applied(banners = 1, icons = 1))
        assertThat(alpha.banner).isSameAs(picture)
        assertThat(alpha.icon).isSameAs(picture)
    }

    @Test
    fun `leaves a banner somebody chose, and a game the database no longer has`() {
        art.onReady()

        assertThat(beta.banner).isSameAs(chosen)
    }
}
