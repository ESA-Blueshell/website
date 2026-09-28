package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.shared.seed.SeedDatabase
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

/**
 * The loader against repositories that keep what they are given, and the ledger on an in-memory
 * database. `BoardSeedLoadIT` checks the same rows against the real schema.
 */
class ShippedBoardsTest {
    private val db = SeedDatabase()
    private val boardRows = BoardSeed.files.rows("boards.csv")
    private val memberRows = BoardSeed.files.rows("members.csv")

    /** Every board ever written, and the numbers of those deleted since. */
    private val written = mutableListOf<Board>()
    private val deleted = mutableSetOf<Int>()
    private val seated = mutableListOf<BoardMember>()

    private val boards =
        mock<BoardRepository>().also { repository ->
            whenever(repository.save(any<Board>())).thenAnswer { call ->
                (call.arguments[0] as Board).also { board ->
                    board.id = written.size + 1L
                    written += board
                }
            }
            whenever(repository.countEverNumbered(any())).thenAnswer { call ->
                written.count { it.number == call.arguments[0] }.toLong()
            }
            whenever(repository.findByNumber(any())).thenAnswer { call ->
                Optional.ofNullable(written.firstOrNull { it.number == call.arguments[0] && it.number !in deleted })
            }
        }

    private val members =
        mock<BoardMemberRepository>().also { repository ->
            whenever(repository.save(any<BoardMember>())).thenAnswer { call ->
                (call.arguments[0] as BoardMember).also { seated += it }
            }
            whenever(repository.countEverNamedOn(any(), any())).thenAnswer { call ->
                seated.count { it.board.id == call.arguments[0] && it.displayName == call.arguments[1] }.toLong()
            }
            whenever(repository.existsByBoardAndUser(any(), any())).thenAnswer { call ->
                seated.any { it.board === call.arguments[0] && it.user === call.arguments[1] }
            }
        }

    private val users = mock<UserService>()

    private fun load() = ShippedBoards(boards, members, users, db.dataSource, db.transactions).apply()

    private fun forget(key: String) = db.jdbc.update("DELETE FROM seed_applied WHERE record_key LIKE ?", key)

    @Test
    fun `the first run writes every row and the second writes none`() {
        assertThat(load()).isEqualTo(ShippedBoards.Applied(boardRows.size, memberRows.size))
        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))
        assertThat(seated).hasSize(memberRows.size)
    }

    @Test
    fun `a board and its members are written as the files have them`() {
        load()

        val row = boardRows.first()
        val board = written.single { it.number == row.getValue("number").toInt() }
        assertThat(board.name).isEqualTo(row.getValue("name").ifBlank { null })
        assertThat(board.candidate).isEqualTo(row.getValue("name").ifBlank { "Board ${board.number}" })
        assertThat(board.startDate).isEqualTo(LocalDate.parse(row.getValue("start_date")))
        assertThat(board.cheer).isEqualTo(row.getValue("cheer").ifBlank { null })
        assertThat(seated.filter { it.board === board }.map { it.startDate }.distinct()).containsExactly(board.startDate)
    }

    @Test
    fun `a database seeded before the ledger records what it holds and writes none of it again`() {
        load()
        db.jdbc.update("DELETE FROM seed_applied")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))
        assertThat(db.count("SELECT COUNT(*) FROM seed_applied")).isEqualTo(boardRows.size + memberRows.size)
    }

    @Test
    fun `a deleted board takes its members out of the run`() {
        load()
        deleted += 3
        db.jdbc.update("DELETE FROM seed_applied WHERE record_key LIKE 'member|3|%'")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))
        assertThat(db.count("SELECT COUNT(*) FROM seed_applied WHERE record_key LIKE 'member|3|%'")).isZero()
    }

    @Test
    fun `a line new to the files is added and attached to the one account that answers to it`() {
        load()
        val louis = mock<User>()
        whenever(users.findOnlyByWrittenName("Louis Hu")).thenReturn(louis)
        seated.removeIf { it.displayName == "Louis Hu" }
        forget("member|%|Louis Hu")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 1))
        assertThat(seated.single { it.displayName == "Louis Hu" }.user).isSameAs(louis)
    }

    @Test
    fun `an account already on the board is not attached a second time`() {
        load()
        val louis = mock<User>()
        whenever(users.findOnlyByWrittenName("Louis Hu")).thenReturn(louis)
        val board = seated.single { it.displayName == "Louis Hu" }.board
        seated.removeIf { it.displayName == "Louis Hu" }
        seated += BoardMember(board = board, user = louis, role = "Chair", startDate = board.startDate)
        forget("member|%|Louis Hu")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 1))
        assertThat(seated.single { it.displayName == "Louis Hu" }.user).isNull()
    }
}
