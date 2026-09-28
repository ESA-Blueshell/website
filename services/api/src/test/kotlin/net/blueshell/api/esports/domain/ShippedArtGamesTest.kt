package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.game.api.GameBlanks
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

/** A game's banner and icon reach it through the game module, which decides whether it still needs one. */
class ShippedArtGamesTest {
    private val stored = mutableListOf<String>()
    private val files =
        mock<FileService> {
            on { store(any(), any(), any(), any(), any()) } doAnswer { call ->
                val name = call.getArgument<String>(1)
                stored += name
                mock<File> { on { path } doReturn "art/$name" }
            }
        }
    private val users = mock<UserService> { on { findByUsername("system") } doReturn mock<User>() }
    private val transactions =
        mock<TransactionTemplate> {
            on { execute(any<TransactionCallback<Int>>()) } doAnswer { it.getArgument<TransactionCallback<Int>>(0).doInTransaction(mock()) }
        }

    private fun art(games: GameBlanks) =
        ShippedArt(files, users, mock<TeamRepository>(), mock<TeamSeasonRepository>(), games, transactions, EsportsSeedFixture.files)

    @Test
    fun `gives every game the banner and icon the seed names, where the game module says it has none`() {
        val games =
            mock<GameBlanks> {
                on { fillBanner(any(), any()) } doAnswer { stores(it.getArgument(1)) }
                on { fillIcon(any(), any()) } doAnswer { stores(it.getArgument(1)) }
            }

        val applied = art(games).apply()

        assertThat(applied.gamePictures).isEqualTo(6)
        verify(games).fillBanner(eq("ALPHA"), any())
        verify(games).fillIcon(eq("GAMMA"), any())
    }

    @Test
    fun `leaves a game that has its pictures alone`() {
        val games =
            mock<GameBlanks> {
                on { fillBanner(any(), any()) } doReturn false
                on { fillIcon(any(), any()) } doReturn false
            }

        assertThat(art(games).apply().gamePictures).isZero()
        assertThat(stored.filter { "banner" in it || "icon" in it }).hasSize(6)
    }

    // A blank the game module fills stores its picture, which is the only way one is stored.
    private fun stores(picture: () -> File): Boolean {
        picture()
        return true
    }
}
