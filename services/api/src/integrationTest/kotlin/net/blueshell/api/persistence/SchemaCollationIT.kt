package net.blueshell.api.persistence

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * The database, its tables and their text columns are utf8mb4_unicode_ci, so every column holds
 * four-byte text and no comparison mixes two collations. The test database starts at the server's
 * default, so this holds only because the changelog makes it so.
 */
@SpringBootTest
class SchemaCollationIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    // Liquibase makes its own two tables at the server's default, before any changeset runs.
    private val outsideLiquibase = "TABLE_NAME NOT IN ('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')"

    @Test
    fun `the database default is utf8mb4_unicode_ci`() {
        val collation =
            jdbc.queryForObject(
                "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()",
                String::class.java,
            )

        assertThat(collation).isEqualTo("utf8mb4_unicode_ci")
    }

    @Test
    fun `every table is utf8mb4_unicode_ci`() {
        val others =
            jdbc.queryForList(
                """
                SELECT CONCAT(TABLE_NAME, ' ', TABLE_COLLATION) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_COLLATION <> 'utf8mb4_unicode_ci'
                  AND $outsideLiquibase
                """.trimIndent(),
                String::class.java,
            )

        assertThat(others).isEmpty()
    }

    @Test
    fun `every text column is utf8mb4_unicode_ci but the JSON ones, which are utf8mb4_bin`() {
        val others =
            jdbc.queryForList(
                """
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' ', COLLATION_NAME) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND COLLATION_NAME <> 'utf8mb4_unicode_ci'
                  AND $outsideLiquibase
                """.trimIndent(),
                String::class.java,
            )

        assertThat(others).containsExactlyInAnyOrder(
            "answers.option_selections utf8mb4_bin",
            "questions.choice_labels utf8mb4_bin",
        )
    }
}
