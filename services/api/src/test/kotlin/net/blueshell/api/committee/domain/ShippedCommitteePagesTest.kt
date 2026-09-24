package net.blueshell.api.committee.domain

import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

class ShippedCommitteePagesTest {
    private val db = SeedDatabase()
    private val pages = ShippedCommitteePages(db.dataSource, db.transactions, SeedCsv("db/seed/committee-fixtures"))

    private fun committee(name: String) = db.jdbc.update("INSERT INTO committees (name) VALUES (?)", name)

    private fun game(code: String) = db.jdbc.update("INSERT INTO game (code) VALUES (?)", code)

    private fun games(name: String): List<String?> =
        db.jdbc.queryForList(
            "SELECT g.game_code FROM committee_games g JOIN committees c ON c.id = g.committee_id WHERE c.name = ? ORDER BY 1",
            String::class.java,
            name,
        )

    @Test
    fun `unlists a committee and links its games once, and the board's later edits outlive the next run`() {
        committee("Board")
        committee("LanCie")
        game("ALPHA")
        game("BETA")

        assertThat(pages.apply()).isEqualTo(3)
        assertThat(db.count("SELECT COUNT(*) FROM committees WHERE name = 'Board' AND listed = FALSE")).isEqualTo(1)
        assertThat(games("LanCie")).containsExactly("ALPHA", "BETA")

        db.jdbc.update("UPDATE committees SET listed = TRUE WHERE name = 'Board'")
        db.jdbc.update("DELETE FROM committee_games WHERE game_code = 'BETA'")

        assertThat(pages.apply()).isZero()
        assertThat(db.count("SELECT COUNT(*) FROM committees WHERE name = 'Board' AND listed = TRUE")).isEqualTo(1)
        assertThat(games("LanCie")).containsExactly("ALPHA")
    }

    @Test
    fun `waits for a committee or a game that is not standing yet, and records a link already there`() {
        committee("LanCie")
        game("ALPHA")
        db.jdbc.update("INSERT INTO committee_games (committee_id, game_code) SELECT id, 'ALPHA' FROM committees WHERE name = 'LanCie'")

        assertThat(pages.apply()).isZero()

        game("BETA")
        assertThat(pages.apply()).isEqualTo(1)
        assertThat(games("LanCie")).containsExactly("ALPHA", "BETA")
    }

    @Test
    fun `a failing run never stops the start`() {
        val failing = mock<ShippedCommitteePages> { on { apply() } doThrow IllegalStateException("down") }
        ShippedCommitteePagesOnStartup(failing).onReady()

        committee("Board")
        ShippedCommitteePagesOnStartup(pages).onReady()
        assertThat(db.count("SELECT COUNT(*) FROM committees WHERE listed = FALSE")).isEqualTo(1)
    }
}
