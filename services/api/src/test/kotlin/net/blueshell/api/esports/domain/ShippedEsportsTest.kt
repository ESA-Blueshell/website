package net.blueshell.api.esports.domain

import net.blueshell.api.shared.seed.SeedDatabase
import net.blueshell.api.testsupport.EsportsSeedFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShippedEsportsTest {
    private val db = SeedDatabase()

    private fun load() = ShippedEsports(db.dataSource, db.transactions, EsportsSeedFixture.files).apply()

    private fun places(): Int = db.count("SELECT COUNT(*) FROM team_roster_entry")

    @Test
    fun `the first run writes every row and the second writes none`() {
        val games = EsportsSeedFixture.GAMES.size
        assertThat(load()).isEqualTo(
            ShippedEsports.Applied(games, EsportsSeedFixture.SEASONS, EsportsSeedFixture.TEAMS, EsportsSeedFixture.ROSTER_PLACES),
        )
        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))
    }

    @Test
    fun `an edit, a rename and a delete all outlive the next run`() {
        load()
        db.jdbc.update("UPDATE game SET name = 'Edited' WHERE code = 'BETA'")
        db.jdbc.update("UPDATE game SET deleted_at = NOW() WHERE code = 'GAMMA'")
        db.jdbc.update("UPDATE season SET end_date = '2000-01-01' WHERE name = 'First 2030'")
        db.jdbc.update("UPDATE team SET name = 'Nomads (renamed)' WHERE name = 'Nomads'")
        db.jdbc.update("UPDATE team_roster_entry SET team_role = 'COACH', handle = 'three-corrected' WHERE handle = 'three'")

        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))

        assertThat(db.jdbc.queryForObject("SELECT name FROM game WHERE code = 'BETA'", String::class.java)).isEqualTo("Edited")
        assertThat(db.count("SELECT COUNT(*) FROM game WHERE code = 'GAMMA' AND deleted_at = '9999-12-31 23:59:59'")).isZero()
        assertThat(db.jdbc.queryForObject("SELECT end_date FROM season WHERE name = 'First 2030'", String::class.java))
            .startsWith("2000-01-01")
        assertThat(db.count("SELECT COUNT(*) FROM team WHERE name = 'Nomads'")).isZero()
        assertThat(places()).isEqualTo(EsportsSeedFixture.ROSTER_PLACES)
    }

    @Test
    fun `a game the files archive is archived once, and stays unarchived when the site says so`() {
        load()
        assertThat(db.count("SELECT COUNT(*) FROM game WHERE code = 'BETA' AND archived")).isEqualTo(1)
        assertThat(db.count("SELECT COUNT(*) FROM game WHERE code = 'ALPHA' AND archived")).isZero()

        db.jdbc.update("UPDATE game SET archived = FALSE WHERE code = 'BETA'")
        load()

        assertThat(db.count("SELECT COUNT(*) FROM game WHERE code = 'BETA' AND archived")).isZero()
    }

    @Test
    fun `a game standing before archiving shipped is archived by the next run`() {
        load()
        db.jdbc.update("UPDATE game SET archived = FALSE WHERE code = 'BETA'")
        db.jdbc.update("DELETE FROM seed_applied WHERE record_key = 'game-archived|BETA'")

        load()

        assertThat(db.count("SELECT COUNT(*) FROM game WHERE code = 'BETA' AND archived")).isEqualTo(1)
    }

    @Test
    fun `a database seeded before the ledger records what it holds and changes none of it`() {
        load()
        db.jdbc.update("DELETE FROM seed_applied")
        db.jdbc.update("UPDATE game SET name = 'Edited' WHERE code = 'BETA'")

        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))

        assertThat(db.jdbc.queryForObject("SELECT name FROM game WHERE code = 'BETA'", String::class.java)).isEqualTo("Edited")
    }

    @Test
    fun `a place new to the files is attached to its player, who takes up the handle`() {
        val user = db.addUser("Player", "One")

        load()

        assertThat(db.count("SELECT COUNT(*) FROM team_roster_entry WHERE user_id = ?", user)).isEqualTo(2)
        assertThat(db.count("SELECT COUNT(*) FROM user_game_account WHERE user_id = ?", user)).isEqualTo(2)
    }

    @Test
    fun `a deleted team leaves its line-up out`() {
        load()
        db.jdbc.update("UPDATE team SET deleted_at = NOW() WHERE name = 'Drifters'")
        db.jdbc.update("DELETE FROM team_roster_entry")
        db.jdbc.update("DELETE FROM seed_applied WHERE record_key LIKE 'entry|%'")

        assertThat(load()).isEqualTo(ShippedEsports.Applied(0, 0, 0, EsportsSeedFixture.ROSTER_PLACES - 2))
    }
}
