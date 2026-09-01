package net.blueshell.api.board.persistence

import db.migration.R__Boards_seed
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Each case starts from the empty database this suite resets to, loads the seed files, and
 * checks what landed. Loading twice is the case that matters: the loader runs on every deploy
 * whose files have moved, on a database that already holds the history.
 */
@SpringBootTest
class BoardSeedLoadIT : UserTestSupport() {

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var boards: BoardRepository

    private val tables = listOf("boards", "board_members")

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE deleted_at = '9999-12-31 23:59:59'", Int::class.java)!!

    private fun board(number: Int): Map<String, Any?> =
        jdbc.queryForMap("SELECT * FROM boards WHERE number = ? AND $ACTIVE", number)

    /**
     * The seat holding one role on one board.
     *
     * Found by the role rather than by the name where a name is what is under test: these
     * columns collate accent- and case-insensitively, so `display_name = 'İlayda Hotamiş'`
     * would happily match a row that had lost both characters, and the assertion would pass
     * against corrupted data.
     */
    private fun seatServing(number: Int, role: String): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT m.* FROM board_members m JOIN boards b ON b.id = m.board_id" +
                " WHERE b.number = ? AND m.role = ? AND m.$ACTIVE AND b.$ACTIVE",
            number,
            role,
        )

    private fun seat(number: Int, name: String): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT m.* FROM board_members m JOIN boards b ON b.id = m.board_id" +
                " WHERE b.number = ? AND m.display_name = ? AND m.$ACTIVE AND b.$ACTIVE",
            number,
            name,
        )

    private fun named(first: String, last: String): User {
        val user = createUserWithRole(Role.MEMBER)
        user.firstName = first
        user.lastName = last
        user.prefix = null
        return userRepository.save(user)
    }

    @Test
    fun `every board and seat in the files lands`() {
        runLoader()

        // The association's own history: nine boards that have sat plus the candidate board
        // standing for the tenth, and forty-six seats across them.
        assertThat(count("boards")).isEqualTo(10)
        assertThat(count("board_members")).isEqualTo(46)
    }

    @Test
    fun `a board nobody has taken a seat on yet lands with no seats and no complaint`() {
        runLoader()

        // The tenth board is a candidate board. A board with no seats is a state that exists
        // from the first deploy, so it is loaded rather than skipped or refused.
        val row = board(10)
        assertThat(row["name"]).isEqualTo("Rainbow road")
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_members m JOIN boards b ON b.id = m.board_id" +
                    " WHERE b.number = 10 AND m.$ACTIVE",
                Int::class.java,
            ),
        ).isZero()
    }

    @Test
    fun `whether a board is in office or a candidate is read off its dates`() {
        runLoader()

        // No column says either, so nothing can disagree with the dates. The ninth board's
        // term contains today; the tenth board's starts after it.
        val today = LocalDate.now()
        assertThat(LocalDate.parse(board(9)["start_date"].toString().take(10))).isBeforeOrEqualTo(today)
        assertThat(LocalDate.parse(board(9)["end_date"].toString().take(10))).isAfterOrEqualTo(today)
        assertThat(LocalDate.parse(board(10)["start_date"].toString().take(10))).isAfter(today)

        // And the unused query that asks which board is running cannot answer with a candidate,
        // because a board whose start date is in the future is not a match for today.
        assertThat(boards.findActiveBoard(today).orElseThrow().number).isEqualTo(9)
    }

    @Test
    fun `the ninth board hands over on the day that was recorded rather than at the year's end`() {
        runLoader()

        // The first handover in the history that is actually known, and the tenth board takes
        // office the day after it, so the line has no gap and no overlap.
        assertThat(board(9)["end_date"].toString()).startsWith("2026-09-16")
        assertThat(board(10)["start_date"].toString()).startsWith("2026-09-17")
    }

    @Test
    fun `running the loader again changes nothing`() {
        runLoader()
        val before = tables.associateWith { count(it) }
        val seat = seat(6, "Roos Kruk")

        runLoader()

        assertThat(tables.associateWith { count(it) }).isEqualTo(before)
        assertThat(seat(6, "Roos Kruk")["id"]).isEqualTo(seat["id"])
    }

    @Test
    fun `a board carries the number, the name and the cheer the file gives it`() {
        runLoader()

        val row = board(7)
        assertThat(row["number"]).isEqualTo(7)
        assertThat(row["name"]).isEqualTo("Overcooked")
        assertThat(row["cheer"]).isEqualTo("Krijg de tering!")
        assertThat(row["candidate"]).isEqualTo("Overcooked")
        assertThat(row["start_date"].toString()).startsWith("2023-09-01")
        assertThat(row["end_date"].toString()).startsWith("2024-08-31")
    }

    @Test
    fun `a board nobody recorded a cheer or a colour for carries none rather than something invented`() {
        runLoader()

        // Seven of the ten cheers and every colour and description are still missing. The page
        // renders nothing rather than a placeholder, which it can only do if the record says
        // there is none.
        val row = board(1)
        assertThat(row["name"]).isEqualTo("Left4dead")
        assertThat(row["cheer"]).isNull()
        assertThat(row["accent"]).isNull()
        assertThat(row["description"]).isNull()
        // `candidate` is NOT NULL and nothing reads it, so it is filled from the name.
        assertThat(row["candidate"]).isEqualTo("Left4dead")
    }

    @Test
    fun `a cheer carrying a comma arrives whole rather than cut at the comma`() {
        runLoader()

        // Two of the ten cheers carry a comma, so the field is quoted in the file. A reader
        // that mis-handled the quoting would land "Blueshell" here and nobody would notice.
        assertThat(board(5)["cheer"]).isEqualTo("Blueshell, always ahead")
        assertThat(board(8)["cheer"]).isEqualTo("RNG, Be With Me!")
    }

    @Test
    fun `a name with an apostrophe in it arrives whole`() {
        runLoader()

        // An apostrophe needs no quoting in a comma-separated field and no escaping in a
        // prepared statement, and this is where either mistake would show.
        assertThat(board(6)["name"]).isEqualTo("Don't starve together")
        assertThat(board(6)["candidate"]).isEqualTo("Don't starve together")
    }

    @Test
    fun `a name written outside ASCII arrives byte for byte`() {
        runLoader()

        // İ is U+0130 and ş is U+015F, neither of them the ASCII letter it resembles. The whole
        // path is under test: the file's bytes, the reader's decoding, the prepared statement
        // and the column's own character set. A connection that was not speaking UTF-8 would
        // have turned both into question marks by the time this reads them back.
        val name = seatServing(5, "Commissioner of External Affairs")["display_name"] as String

        assertThat(name).isEqualTo("İlayda Hotamiş")
        assertThat(name).isEqualTo("\u0130layda Hotami\u015F")
        assertThat(name.map { it.code }).startsWith(0x0130).endsWith(0x015F)
    }

    @Test
    fun `the columns this history is written into hold more than ASCII`() {
        // Not a property of the seed but the reason it can carry these names at all. A column
        // that had come out as latin1 would fail the case above with no hint as to why.
        val charsets = jdbc.queryForList(
            """
            SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_SET_NAME
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND ((TABLE_NAME = 'boards' AND COLUMN_NAME IN ('name', 'candidate', 'cheer', 'accent', 'description'))
                OR (TABLE_NAME = 'board_members' AND COLUMN_NAME IN ('display_name', 'nickname', 'role', 'description')))
            """.trimIndent(),
        )

        assertThat(charsets).hasSize(9)
        assertThat(charsets).allSatisfy { row -> assertThat(row["CHARACTER_SET_NAME"]).isEqualTo("utf8mb4") }
    }

    @Test
    fun `the recorded name is split from the nickname that used to sit inside it`() {
        runLoader()

        // `Roos "SkyeWolf" Kruk` was one string. Nothing could ask for the name without the
        // quotes in the middle of it, which is why no seat before the seventh board has ever
        // been attached to an account.
        val row = seat(6, "Roos Kruk")
        assertThat(row["nickname"]).isEqualTo("SkyeWolf")
        assertThat(row["role"]).isEqualTo("Commissioner of Internal Affairs")
    }

    @Test
    fun `a nickname the blurb states is recorded beside the name`() {
        runLoader()

        assertThat(seat(8, "Joris Jonkers")["nickname"]).isEqualTo("ExtraToast")
        assertThat(seatServing(2, "Secretary")["display_name"]).isEqualTo("Kimberly Evertsz")
        assertThat(seat(9, "Rene Hammink")["nickname"]).isEqualTo("Mr. Pancake^-^")
    }

    @Test
    fun `a seat nobody recorded a nickname for carries none`() {
        runLoader()

        // None of the seventh board's blurbs states one, so all five stand on their names.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_members m JOIN boards b ON b.id = m.board_id" +
                    " WHERE b.number = 7 AND m.nickname IS NULL AND m.$ACTIVE",
                Int::class.java,
            ),
        ).isEqualTo(5)
    }

    @Test
    fun `the photographs the frontend ships are still named on the records that draw them`() {
        runLoader()

        // Uploaded pictures come later. Until then these asset names are what the board page
        // draws, so the files carry them and the seed writes them.
        assertThat(board(9)["image"]).isEqualTo("board9/board9.jpg")
        assertThat(board(1)["image"]).isNull()
        assertThat(seat(6, "Amber Scholtz")["image"]).isEqualTo("board6/amber.jpg")
    }

    @Test
    fun `a seat is served for as long as its board sits`() {
        runLoader()

        // The files carry no dates of their own for a seat, so the board's own are what a seat
        // starts with -- including the ninth board's real handover date.
        val row = seat(9, "Emma Dokter")
        assertThat(row["start_date"].toString()).startsWith("2025-09-01")
        assertThat(row["end_date"].toString()).startsWith("2026-09-16")
    }

    @Test
    fun `a blurb carrying a comma, a quote and a line of its own arrives whole`() {
        runLoader()

        // The one reader across the application, so a quoted blurb cannot parse two ways.
        val blurb = seat(9, "Taha Aydin")["description"] as String
        assertThat(blurb).startsWith("Hi my name is Taha 'Talpa' Aydin,")
        assertThat(blurb).contains("\n")
        assertThat(blurb).endsWith("make it a fun year for all of us.")
    }

    @Test
    fun `a corrected row is applied on the next run`() {
        runLoader()
        jdbc.update("UPDATE boards SET name = 'Something Else', cheer = NULL WHERE number = 8")
        jdbc.update("UPDATE board_members SET nickname = 'wrong' WHERE display_name = 'Chris Wong'")

        runLoader()

        // The files are the reviewed record, so the database is brought back to what they say.
        assertThat(board(8)["name"]).isEqualTo("Wasted")
        assertThat(board(8)["cheer"]).isEqualTo("RNG, Be With Me!")
        assertThat(seat(8, "Chris Wong")["nickname"]).isEqualTo("FetaBass")
    }

    @Test
    fun `a deleted board is left deleted rather than resurrected by the next run`() {
        runLoader()
        jdbc.update("UPDATE boards SET deleted_at = NOW(6) WHERE number = 3")

        runLoader()

        // Its row is still in the file. Removing the row is how a board leaves for good.
        assertThat(count("boards")).isEqualTo(9)
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM boards WHERE number = 3 AND $ACTIVE", Int::class.java),
        ).isZero()
    }

    @Test
    fun `a deleted seat is left deleted rather than seated again by the next run`() {
        runLoader()
        jdbc.update("UPDATE board_members SET deleted_at = NOW(6) WHERE display_name = 'Louis Hu'")

        runLoader()

        assertThat(count("board_members")).isEqualTo(45)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_members WHERE display_name = 'Louis Hu' AND $ACTIVE",
                Int::class.java,
            ),
        ).isZero()
    }

    @Test
    fun `a seat is attached to the one account that answers to its name`() {
        val user = named("Roos", "Kruk")

        runLoader()

        // The split name is what makes this possible at all: the recorded string used to carry
        // the nickname in quotes, and matched nobody.
        assertThat(user.fullName).isEqualTo("Roos Kruk")
        assertThat(seat(6, "Roos Kruk")["user_id"]).isEqualTo(user.id)
    }

    @Test
    fun `a name nobody answers to leaves the seat standing under its own name`() {
        runLoader()

        assertThat(seat(1, "Thijs Lieverse")["user_id"]).isNull()
    }

    @Test
    fun `a name two accounts answer to leaves the seat standing under its own name`() {
        named("Amber", "Scholtz")
        named("Amber", "Scholtz")

        runLoader()

        // Guessing between two people is worse than leaving it for somebody to resolve.
        assertThat(seat(6, "Amber Scholtz")["user_id"]).isNull()
    }

    @Test
    fun `a seat detached afterwards is never attached again`() {
        val user = named("Roos", "Kruk")
        runLoader()
        assertThat(seat(6, "Roos Kruk")["user_id"]).isEqualTo(user.id)

        jdbc.update("UPDATE board_members SET user_id = NULL WHERE display_name = 'Roos Kruk'")
        runLoader()

        // Detaching somebody says who they are not. A step that re-matched would undo that
        // every time the application came up.
        assertThat(seat(6, "Roos Kruk")["user_id"]).isNull()
    }

    private fun runLoader() {
        dataSource.connection.use { connection ->
            R__Boards_seed().migrate(object : Context {
                override fun getConfiguration() = null

                override fun getConnection(): Connection = connection
            })
        }
    }

    private companion object {
        const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"
    }
}
