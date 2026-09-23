package net.blueshell.api.board.domain

import net.blueshell.api.shared.seed.SeedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShippedBoardsTest {
    private val db = SeedDatabase()
    private val boards = BoardSeed.files.rows("boards.csv")
    private val members = BoardSeed.files.rows("members.csv")

    private fun load() = ShippedBoards(db.dataSource, db.transactions).apply()

    private fun cheer(number: Int): String? =
        db.jdbc.queryForObject("SELECT cheer FROM boards WHERE number = ?", String::class.java, number)

    private fun named(name: String): Int = db.count("SELECT COUNT(*) FROM board_members WHERE display_name = ?", name)

    @Test
    fun `the first run writes every row and the second writes none`() {
        assertThat(load()).isEqualTo(ShippedBoards.Applied(boards.size, members.size))
        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))
        assertThat(db.count("SELECT COUNT(*) FROM board_members")).isEqualTo(members.size)
    }

    @Test
    fun `an edit, a rename and a hard delete all outlive the next run`() {
        load()
        db.jdbc.update("UPDATE boards SET cheer = 'edited' WHERE number = 8")
        db.jdbc.update("UPDATE board_members SET display_name = 'Christopher Wong' WHERE display_name = 'Chris Wong'")
        db.jdbc.update("DELETE FROM board_members WHERE display_name = 'Louis Hu'")
        db.jdbc.update("DELETE FROM boards WHERE number = 3")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))

        assertThat(cheer(8)).isEqualTo("edited")
        assertThat(named("Chris Wong")).isZero()
        assertThat(named("Louis Hu")).isZero()
        assertThat(db.count("SELECT COUNT(*) FROM boards WHERE number = 3")).isZero()
    }

    @Test
    fun `a database seeded before the ledger records what it holds and changes none of it`() {
        load()
        db.jdbc.update("DELETE FROM seed_applied")
        db.jdbc.update("UPDATE boards SET cheer = 'edited' WHERE number = 8")
        db.jdbc.update("UPDATE boards SET deleted_at = NOW() WHERE number = 3")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 0))

        assertThat(cheer(8)).isEqualTo("edited")
        assertThat(db.count("SELECT COUNT(*) FROM seed_applied")).isEqualTo(boards.size + members.size - membersOf(3))
    }

    @Test
    fun `a line new to the files is added and attached to the one account that answers to it`() {
        load()
        val user = db.addUser("Louis", "Hu")
        db.jdbc.update("DELETE FROM board_members WHERE display_name = 'Louis Hu'")
        db.jdbc.update("DELETE FROM seed_applied WHERE record_key LIKE 'member|%|Louis Hu'")

        assertThat(load()).isEqualTo(ShippedBoards.Applied(0, 1))

        assertThat(
            db.jdbc.queryForObject("SELECT user_id FROM board_members WHERE display_name = 'Louis Hu'", Long::class.java),
        ).isEqualTo(user)
    }

    private fun membersOf(number: Int): Int = members.count { it.getValue("board") == number.toString() }
}
