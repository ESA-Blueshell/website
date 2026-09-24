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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory

/** What Spring hands the service: every bean of a kind, looked up when asked. */
inline fun <reified T : Any> provider(vararg beans: T): ObjectProvider<T> =
    StaticListableBeanFactory()
        .apply { beans.forEachIndexed { index, bean -> addBean("bean$index", bean) } }
        .getBeanProvider(T::class.java)

class GameServiceTest {
    private val games =
        mock<GameRepository> {
            on { save(any<Game>()) } doAnswer { it.getArgument(0) }
            // Mockito answers a nullable Long with 0, which would read as a removed game holding every code.
            on { findRemovedIdByCode(any()) } doReturn null
        }
    private val pictures = mock<StoredPictures>()
    private val refused = mutableListOf<String>()
    private val holding =
        object : GameHoldings {
            override fun refuseRemoval(code: String) {
                refused += code
            }
        }
    private val service = GameService(games, pictures, provider(holding), provider(GamesInCompetition { setOf("CHESS") }))

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
    fun `asks the module that fields teams which games are in competition`() {
        assertThat(service.inCompetition()).containsExactly("CHESS")
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
        assertThat(service.update("CHESS", "Chess club", "chess-club", null, null, null, null, null).sortIndex).isEqualTo(3)
        assertThatThrownBy { service.update("CHESS", " ", "chess", null, null, null, null, 0) }
            .isInstanceOf(GameNameBlank::class.java)
    }

    @Test
    fun `archives a game and brings it back`() {
        val chess = game("CHESS")
        whenever(games.findByCode("CHESS")).thenReturn(chess)

        assertThat(service.archive("CHESS", true).archived).isTrue()
        assertThat(service.archive("CHESS", false).archived).isFalse()
    }

    @Test
    fun `removes an archived game once every module holding something against it has agreed, keeping its row`() {
        val chess = game("CHESS").apply { archived = true }
        whenever(games.findByCode("CHESS")).thenReturn(chess)

        service.remove("CHESS")

        assertThat(refused).containsExactly("CHESS")
        verify(games).remove(1)
        verify(games, never()).delete(any<Game>())
    }

    @Test
    fun `refuses to remove a game that is still played, unless told to archive it in the same step`() {
        val chess = game("CHESS")
        whenever(games.findByCode("CHESS")).thenReturn(chess)

        assertThatThrownBy { service.remove("CHESS") }.isInstanceOf(GameNotArchived::class.java)
        verify(games, never()).remove(any())

        service.remove("CHESS", archiveFirst = true)
        assertThat(chess.archived).isTrue()
        verify(games).remove(1)
    }

    @Test
    fun `keeps a game a holding refuses to let go`() {
        val chess = game("CHESS").apply { archived = true }
        whenever(games.findByCode("CHESS")).thenReturn(chess)
        val refusing = object : GameHoldings {
            override fun refuseRemoval(code: String) = throw IllegalStateException("held")
        }
        val strict = GameService(games, pictures, provider(refusing), provider())

        assertThatThrownBy { strict.remove("CHESS") }.hasMessage("held")
        verify(games, never()).remove(any())
    }

    @Test
    fun `adds up what every module holds against a game`() {
        whenever(games.findByCode("CHESS")).thenReturn(game("CHESS"))
        val teams = object : GameHoldings {
            override fun heldAgainst(code: String) = mapOf("teams" to 2L, "people" to 9L)
        }
        val events = object : GameHoldings {
            override fun heldAgainst(code: String) = mapOf("events" to 4L, "teams" to 1L)
        }
        val counting = GameService(games, pictures, provider(teams, events), provider())

        assertThat(counting.heldAgainst("CHESS")).isEqualTo(mapOf("teams" to 3L, "people" to 9L, "events" to 4L))
        assertThat(service.heldAgainst("CHESS")).isEmpty()
    }

    @Test
    fun `brings a removed game back when it is added again, with what was typed`() {
        val restored = game("CHESS", slug = "old-chess", sortIndex = 6)
        whenever(games.findRemovedIdByCode("CHESS")).thenReturn(1)
        whenever(games.findByCode("CHESS")).thenReturn(null, restored)

        val added = service.create(name = "Chess", slug = "chess", intro = "Blitz")

        verify(games).restore(1, "chess")
        assertThat(added.slug).isEqualTo("chess")
        assertThat(added.intro).isEqualTo("Blitz")
        assertThat(added.sortIndex).isEqualTo(6)
    }

    @Test
    fun `a picture that was never stored refuses the save`() {
        whenever(pictures.of("nowhere.webp", FileType.GAME_ICON)) doThrow IllegalArgumentException("not stored")
        whenever(games.findAllByOrderBySortIndexAsc()).thenReturn(emptyList())

        assertThatThrownBy { service.create(name = "Chess", slug = "chess", icon = "nowhere.webp") }.hasMessage("not stored")
    }
}
