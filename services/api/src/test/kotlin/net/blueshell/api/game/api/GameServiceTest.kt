package net.blueshell.api.game.api

import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.file.persistence.File
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameRepository
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GameServiceTest {
    private val games = mock<GameRepository> { on { save(any<Game>()) } doAnswer { it.getArgument(0) } }
    private val pictures = mock<StoredPictures>()
    private val refused = mutableListOf<String>()
    private val holding = GameHoldings { code -> refused += code }
    private val service = GameService(games, pictures, listOf(holding))

    private fun game(
        code: String,
        name: String = code,
        slug: String = code.lowercase(),
        sortIndex: Int = 0,
        id: Long = 1,
    ) = Game(code = code, name = name, slug = slug, sortIndex = sortIndex).apply { this.id = id }

    @Test
    fun `lists every game in its place, and offers their codes in the same order`() {
        val all = listOf(game("CHESS"), game("WORDLE", id = 2))
        whenever(games.findAllByOrderBySortIndexAsc()).thenReturn(all)

        assertThat(service.findAll()).isEqualTo(all)
        assertThat(service.codes()).containsExactly("CHESS", "WORDLE")
    }

    @Test
    fun `finds a game by its code, and refuses a code nobody holds`() {
        whenever(games.findByCode("CHESS")).thenReturn(game("CHESS"))

        assertThat(service.findByCode(" CHESS ").code).isEqualTo("CHESS")
        assertThatThrownBy { service.requireGame("PONG") }.isInstanceOf(UnknownGameCode::class.java)
    }

    @Test
    fun `finds a game by its address whatever its case`() {
        whenever(games.findBySlug("chess")).thenReturn(game("CHESS"))

        assertThat(service.findBySlug(" Chess ")?.code).isEqualTo("CHESS")
    }

    @Test
    fun `adds a game with a code made from its name, placed after the last one`() {
        val banner = mock<File>()
        whenever(pictures.of("games/b.webp", FileType.GAME_BANNER)).thenReturn(banner)
        whenever(games.findAllByOrderBySortIndexAsc()).thenReturn(listOf(game("CHESS", sortIndex = 4)))

        val added =
            service.create(name = " Rocket League! ", slug = "Rocket League", intro = "  ", accent = " #1183d6 ", banner = "games/b.webp")

        assertThat(added.code).isEqualTo("ROCKET_LEAGUE")
        assertThat(added.name).isEqualTo("Rocket League!")
        assertThat(added.slug).isEqualTo("rocket-league")
        assertThat(added.intro).isNull()
        assertThat(added.accent).isEqualTo("#1183d6")
        assertThat(added.banner).isSameAs(banner)
        assertThat(added.sortIndex).isEqualTo(5)
    }

    @Test
    fun `adds the first game at the start, or where it is told to go`() {
        whenever(games.findAllByOrderBySortIndexAsc()).thenReturn(emptyList())

        assertThat(service.create(name = "Chess", slug = "chess").sortIndex).isEqualTo(1)
        assertThat(service.create(name = "Go", slug = "go", sortIndex = 7).sortIndex).isEqualTo(7)
    }

    @Test
    fun `refuses a game without a usable name, one that exists, or an address it cannot have`() {
        whenever(games.findByCode("CHESS")).thenReturn(game("CHESS"))
        whenever(games.findBySlug("taken")).thenReturn(game("OTHER", id = 9))

        assertThatThrownBy { service.create(name = "  ", slug = "x") }.isInstanceOf(GameNameBlank::class.java)
        assertThatThrownBy { service.create(name = "!!!", slug = "x") }.isInstanceOf(GameNameUnusable::class.java)
        assertThatThrownBy { service.create(name = "Chess", slug = "chess") }.isInstanceOf(GameAlreadyExists::class.java)
        assertThatThrownBy { service.create(name = "Go", slug = " -- ") }.isInstanceOf(GameAddressBlank::class.java)
        assertThatThrownBy { service.create(name = "Go", slug = "competitive-scene") }.isInstanceOf(AddressReserved::class.java)
        assertThatThrownBy { service.create(name = "Go", slug = "taken") }.isInstanceOf(AddressTaken::class.java)
    }

    @Test
    fun `corrects everything about a game but its code`() {
        val chess = game("CHESS")
        whenever(games.findByCode("CHESS")).thenReturn(chess)
        whenever(games.findBySlug("chess-club")).thenReturn(chess)

        val saved = service.update("CHESS", " Chess club ", "chess-club", " Blitz ", null, null, null, 3)

        assertThat(saved.code).isEqualTo("CHESS")
        assertThat(saved.name).isEqualTo("Chess club")
        assertThat(saved.slug).isEqualTo("chess-club")
        assertThat(saved.intro).isEqualTo("Blitz")
        assertThat(saved.sortIndex).isEqualTo(3)
        assertThatThrownBy { service.update("CHESS", " ", "chess", null, null, null, null, 0) }
            .isInstanceOf(GameNameBlank::class.java)
    }

    @Test
    fun `removes a game once every module holding something against it has agreed`() {
        val chess = game("CHESS")
        whenever(games.findByCode("CHESS")).thenReturn(chess)

        service.delete("CHESS")

        assertThat(refused).containsExactly("CHESS")
        verify(games).delete(chess)
    }

    @Test
    fun `keeps a game a holding refuses to let go`() {
        val chess = game("CHESS")
        whenever(games.findByCode("CHESS")).thenReturn(chess)
        val refusing = GameHoldings { _ -> throw IllegalStateException("held") }
        val strict = GameService(games, pictures, listOf(refusing))

        assertThatThrownBy { strict.delete("CHESS") }.hasMessage("held")
        verify(games, never()).delete(any<Game>())
    }

    @Test
    fun `a picture that was never stored refuses the save`() {
        whenever(pictures.of("nowhere.webp", FileType.GAME_ICON)) doThrow IllegalArgumentException("not stored")
        whenever(games.findAllByOrderBySortIndexAsc()).thenReturn(emptyList())

        assertThatThrownBy { service.create(name = "Chess", slug = "chess", icon = "nowhere.webp") }.hasMessage("not stored")
    }
}
