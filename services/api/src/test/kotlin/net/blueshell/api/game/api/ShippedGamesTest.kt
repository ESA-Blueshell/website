package net.blueshell.api.game.api

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ShippedGamesTest {
    private val repository = mock<GameRepository>()
    private val games = ShippedGames(repository)

    @Test
    fun `a game counts as held while it stands and after it is removed`() {
        whenever(repository.findByCode("ALPHA")).thenReturn(Game(code = "ALPHA", name = "Alpha", slug = "alpha"))
        // Mockito answers a boxed Long with 0, not null, so the codes nobody removed say so.
        whenever(repository.findRemovedIdByCode(any())).thenReturn(null)
        whenever(repository.findRemovedIdByCode("BETA")).thenReturn(7L)

        assertThat(games.everHeld("ALPHA")).isTrue()
        assertThat(games.everHeld("BETA")).isTrue()
        assertThat(games.everHeld("GAMMA")).isFalse()
        assertThat(games.stands("ALPHA")).isTrue()
        assertThat(games.stands("BETA")).isFalse()
    }

    @Test
    fun `adds a game under the code the files give it`() {
        games.add(ShippedGame("ALPHA", "Alpha", "alpha", "#112233", 4, "Intro", archived = true))

        val saved = argumentCaptor<Game>()
        verify(repository).save(saved.capture())
        with(saved.firstValue) {
            assertThat(code).isEqualTo("ALPHA")
            assertThat(slug).isEqualTo("alpha")
            assertThat(accent).isEqualTo("#112233")
            assertThat(sortIndex).isEqualTo(4)
            assertThat(intro).isEqualTo("Intro")
            assertThat(archived).isTrue()
        }
    }

    @Test
    fun `archives a standing game and leaves a removed one be`() {
        val alpha = Game(code = "ALPHA", name = "Alpha", slug = "alpha")
        whenever(repository.findByCode(any())).thenReturn(null)
        whenever(repository.findByCode("ALPHA")).thenReturn(alpha)

        games.archive("ALPHA")
        games.archive("GONE")

        assertThat(alpha.archived).isTrue()
    }
}
