package net.blueshell.api.shared.seed

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * An in-memory database holding only the columns the seed loaders touch, so they run in the unit
 * suite. The integration suite checks them against the real schema.
 */
class SeedDatabase {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:h2:mem:seed-${UUID.randomUUID()};MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        )
    val jdbc = JdbcTemplate(dataSource)
    val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))

    init {
        SCHEMA.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach(jdbc::execute)
    }

    fun count(
        sql: String,
        vararg args: Any,
    ): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    fun addUser(
        first: String,
        last: String,
    ): Long {
        jdbc.update("INSERT INTO users (first_name, last_name) VALUES (?, ?)", first, last)
        return jdbc.queryForObject("SELECT MAX(id) FROM users", Long::class.java)!!
    }

    private companion object {
        const val LIVE = "deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59'"

        val SCHEMA =
            """
            CREATE TABLE seed_applied (
                seed VARCHAR(32) NOT NULL, record_key VARCHAR(512) NOT NULL,
                applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (seed, record_key));
            CREATE TABLE users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, first_name VARCHAR(255), prefix VARCHAR(255),
                last_name VARCHAR(255), $LIVE);
            CREATE TABLE boards (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, number INT NOT NULL, name VARCHAR(255),
                candidate VARCHAR(255) NOT NULL, cheer VARCHAR(255), accent VARCHAR(255), description TEXT,
                start_date DATE NOT NULL, end_date DATE, $LIVE);
            CREATE TABLE board_members (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, board_id BIGINT NOT NULL, user_id BIGINT,
                display_name VARCHAR(255), nickname VARCHAR(255), role VARCHAR(255), description TEXT,
                start_date DATE, end_date DATE, $LIVE);
            CREATE TABLE game (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(32) NOT NULL, name VARCHAR(255),
                slug VARCHAR(255), accent VARCHAR(255), sort_index INT, intro TEXT,
                archived BOOLEAN NOT NULL DEFAULT FALSE, $LIVE);
            CREATE TABLE season (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, start_date DATE,
                end_date DATE, $LIVE);
            CREATE TABLE team (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, $LIVE);
            CREATE TABLE season_game (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, season_id BIGINT NOT NULL, game VARCHAR(32) NOT NULL, $LIVE);
            CREATE TABLE team_season (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, team_id BIGINT NOT NULL, game VARCHAR(32) NOT NULL,
                season_id BIGINT NOT NULL, $LIVE);
            CREATE TABLE team_roster_entry (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, team_season_id BIGINT NOT NULL, handle VARCHAR(255),
                team_role VARCHAR(32), display_name VARCHAR(255), sort_index INT, user_id BIGINT, $LIVE);
            CREATE TABLE committees (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL,
                listed BOOLEAN NOT NULL DEFAULT TRUE, $LIVE);
            CREATE TABLE committee_games (
                committee_id BIGINT NOT NULL, game_code VARCHAR(32) NOT NULL, PRIMARY KEY (committee_id, game_code));
            CREATE TABLE user_game_account (
                id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, game VARCHAR(32) NOT NULL,
                handle VARCHAR(255), $LIVE)
            """.trimIndent()
    }
}
