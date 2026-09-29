package net.blueshell.api.platform.migration

import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.sql.DriverManager

class ShortcodesBecomeCharactersTest {
    private val connection = DriverManager.getConnection("jdbc:h2:mem:shortcodes;MODE=MariaDB;DB_CLOSE_DELAY=-1")
    private val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))

    init {
        connection.createStatement().use { sql ->
            for (table in listOf("events", "committees", "sponsors", "boards", "board_members", "team_roster_entry")) {
                sql.execute("CREATE TABLE $table (id BIGINT PRIMARY KEY, description TEXT)")
            }
            sql.execute("CREATE TABLE questions (id BIGINT PRIMARY KEY, label TEXT)")
            sql.execute("CREATE TABLE game (id BIGINT PRIMARY KEY, intro TEXT, competition_intro TEXT)")
            sql.execute(
                "CREATE TABLE shortcode_rewrites (table_name VARCHAR(64), column_name VARCHAR(64), row_id BIGINT, " +
                    "before_text TEXT, PRIMARY KEY (table_name, column_name, row_id))",
            )
            sql.execute("INSERT INTO events VALUES (1, 'LAN :tada: at 12:30:00'), (2, 'No shortcode'), (3, NULL), (4, '`:tada:`')")
            sql.execute("INSERT INTO questions VALUES (7, 'Coming? :+1:')")
            sql.execute("INSERT INTO game VALUES (9, ':video_game:', 'Play :smile: :smile:')")
        }
    }

    @AfterEach
    fun close() {
        connection.createStatement().use { it.execute("DROP ALL OBJECTS") }
        connection.close()
    }

    private fun column(
        table: String,
        column: String,
        id: Long,
    ): String? =
        connection.createStatement().use { sql ->
            sql.executeQuery("SELECT $column FROM $table WHERE id = $id").use { rows -> rows.next().let { rows.getString(1) } }
        }

    @Test
    fun `writes shortcodes as emoji, keeps each value as it was, says how many, and a rollback writes them back`() {
        val change = ShortcodesBecomeCharacters().apply { setUp() }
        change.setFileOpener(mock<ResourceAccessor>())

        change.execute(database)

        assertThat(column("events", "description", 1)).isEqualTo("LAN 🎉 at 12:30:00")
        assertThat(column("events", "description", 2)).isEqualTo("No shortcode")
        assertThat(column("events", "description", 4)).isEqualTo("`:tada:`")
        assertThat(column("questions", "label", 7)).isEqualTo("Coming? 👍")
        assertThat(column("game", "intro", 9)).isEqualTo("🎮")
        assertThat(column("game", "competition_intro", 9)).isEqualTo("Play 😄 😄")
        assertThat(change.confirmationMessage).startsWith("Wrote 5 shortcodes as emoji in 4 descriptions")
        val kept =
            connection.createStatement().use { sql ->
                sql.executeQuery("SELECT COUNT(*) FROM shortcode_rewrites").use { rows -> rows.next().let { rows.getInt(1) } }
            }
        assertThat(kept).isEqualTo(4)

        change.rollback(database)

        assertThat(column("events", "description", 1)).isEqualTo("LAN :tada: at 12:30:00")
        assertThat(column("events", "description", 2)).isEqualTo("No shortcode")
        assertThat(column("questions", "label", 7)).isEqualTo("Coming? :+1:")
        assertThat(column("game", "competition_intro", 9)).isEqualTo("Play :smile: :smile:")
        assertThat(change.validate(database).hasErrors()).isFalse()
    }
}
