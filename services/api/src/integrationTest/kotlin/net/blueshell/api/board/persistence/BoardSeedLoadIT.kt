package net.blueshell.api.board.persistence

import net.blueshell.api.board.domain.BoardSeed
import net.blueshell.api.board.domain.ShippedBoards
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Each case starts from the empty database this suite resets to, loads the seed files, and
 * checks what landed. Loading twice is the case that matters: the loader runs on every start,
 * on a database that already holds the history and the edits made to it since.
 */
@SpringBootTest
class BoardSeedLoadIT : UserTestSupport() {
    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var boards: BoardRepository

    private val tables = listOf("boards", "board_members")

    /**
     * What the files say, read the way the loader reads them.
     *
     * The expectations here come out of the seed rather than being written next to it. What is under test is
     * the loader: that everything the files hold lands, once each, and survives a second run. A number typed
     * into the assertion tests the file instead, and rots the day somebody joins a board: four cases here
     * were asserting forty-six members against a file that had grown to fifty-two. The files' own facts are
     * asserted where they belong, in `BoardSeedParsingTest`.
     */
    private val seededBoards get() = BoardSeed.files.rows("boards.csv")

    private val seededMembers get() = BoardSeed.files.rows("members.csv")

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE deleted_at = '9999-12-31 23:59:59'", Int::class.java)!!

    private fun board(number: Int): Map<String, Any?> = jdbc.queryForMap("SELECT * FROM boards WHERE number = ? AND $ACTIVE", number)

    /**
     * The member holding one role on one board.
     *
     * Found by the role rather than by the name where a name is what is under test: these
     * columns collate accent- and case-insensitively, so `display_name = 'İlayda Hotamiş'`
     * would happily match a row that had lost both characters, and the assertion would pass
     * against corrupted data.
     */
    private fun memberServing(
        number: Int,
        role: String,
    ): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT m.* FROM board_members m JOIN boards b ON b.id = m.board_id" +
                " WHERE b.number = ? AND m.role = ? AND m.$ACTIVE AND b.$ACTIVE",
            number,
            role,
        )

    private fun member(
        number: Int,
        name: String,
    ): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT m.* FROM board_members m JOIN boards b ON b.id = m.board_id" +
                " WHERE b.number = ? AND m.display_name = ? AND m.$ACTIVE AND b.$ACTIVE",
            number,
            name,
        )

    private fun named(
        first: String,
        last: String,
    ): User {
        val user = createUserWithRole(Role.MEMBER)
        user.firstName = first
        user.lastName = last
        user.prefix = null
        return userRepository.save(user)
    }

    @Test
    fun `every board and member in the files lands`() {
        runLoader()

        // The association's own history: every board the file records, and every member across
        // them, each landing once.
        assertThat(count("boards")).isEqualTo(seededBoards.size)
        assertThat(count("board_members")).isEqualTo(seededMembers.size)
    }

    @Test
    fun `a board written down before it took office lands with whatever members the file gives it`() {
        runLoader()

        // The tenth board was written down and joined before it took office. It had no members
        // at all when it was first recorded, and a board with none is a state that exists from
        // the first deploy, so the count comes from the file rather than from either assumption.
        val row = board(10)
        assertThat(row["name"]).isEqualTo("Rainbow road")
        assertThat(membersOn(10)).isEqualTo(seededMembers.count { it.getValue("board") == "10" })
    }

    @Test
    fun `whether a board is in office or a candidate is read off its dates`() {
        runLoader()

        // No column says either, so nothing can disagree with the dates. Which board is in office
        // is not named here: the seed records real handovers and the days it names arrive, so
        // naming one dates the test to the afternoon it was written. This asked for the ninth
        // until the morning the tenth took office.
        val today = LocalDate.now()
        val inOffice =
            seededBoards.singleOrNull { start(it) <= today && today <= end(it) }
                ?: throw AssertionError("the files record no board whose term contains $today; the next board is missing")

        val row = board(number(inOffice))
        assertThat(LocalDate.parse(row["start_date"].toString().take(10))).isBeforeOrEqualTo(today)
        assertThat(LocalDate.parse(row["end_date"].toString().take(10))).isAfterOrEqualTo(today)

        // The query answers with the board the dates select rather than with the last board
        // recorded, and a board dated ahead of today is not a match for today.
        assertThat(boards.findActiveBoard(today).orElseThrow().number).isEqualTo(number(inOffice))

        // A candidate is there only while the files record a board after the one in office: the
        // last board in the file has nobody following it, and then there is nothing to assert.
        // The day it takes office is the day the query starts answering with it and not before.
        seededBoards.filter { start(it) > today }.minByOrNull { start(it) }?.let { candidate ->
            assertThat(LocalDate.parse(board(number(candidate))["start_date"].toString().take(10))).isAfter(today)
            assertThat(boards.findActiveBoard(start(candidate)).orElseThrow().number).isEqualTo(number(candidate))
            assertThat(boards.findActiveBoard(start(candidate).minusDays(1)).orElseThrow().number)
                .isEqualTo(number(inOffice))
        }
    }

    @Test
    fun `the board in office on a past date is the one whose term held that date`() {
        runLoader()

        // Read at dates the history has already fixed, so what the query does is pinned without
        // depending on the day the suite runs.
        assertThat(boards.findActiveBoard(LocalDate.parse("2024-01-01")).orElseThrow().number).isEqualTo(7)
        assertThat(boards.findActiveBoard(LocalDate.parse("2023-08-31")).orElseThrow().number).isEqualTo(6)
        assertThat(boards.findActiveBoard(LocalDate.parse("2016-01-01"))).isEmpty()
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
        val roos = member(6, "Roos Kruk")

        assertThat(runLoader()).isEqualTo(ShippedBoards.Applied(boards = 0, members = 0))

        assertThat(tables.associateWith { count(it) }).isEqualTo(before)
        assertThat(member(6, "Roos Kruk")["id"]).isEqualTo(roos["id"])
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
        val name = memberServing(5, "Commissioner of External Affairs")["display_name"] as String

        assertThat(name).isEqualTo("İlayda Hotamiş")
        assertThat(name).isEqualTo("\u0130layda Hotami\u015F")
        assertThat(name.map { it.code }).startsWith(0x0130).endsWith(0x015F)
    }

    @Test
    fun `the columns this history is written into hold more than ASCII`() {
        // Not a property of the seed but the reason it can carry these names at all. A column
        // that had come out as latin1 would fail the case above with no hint as to why.
        val charsets =
            jdbc.queryForList(
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
        // quotes in the middle of it, which is why no member before the seventh board has ever
        // been attached to an account.
        val row = member(6, "Roos Kruk")
        assertThat(row["nickname"]).isEqualTo("SkyeWolf")
        assertThat(row["role"]).isEqualTo("Commissioner of Internal Affairs")
    }

    @Test
    fun `a nickname the blurb states is recorded beside the name`() {
        runLoader()

        assertThat(member(8, "Joris Jonkers")["nickname"]).isEqualTo("ExtraToast")
        assertThat(memberServing(2, "Secretary")["display_name"]).isEqualTo("Kimberly Evertsz")
        assertThat(member(9, "Rene Hammink")["nickname"]).isEqualTo("Mr. Pancake^-^")
    }

    @Test
    fun `a member nobody recorded a nickname for carries none`() {
        runLoader()

        // Twenty-odd members in the history have no nickname recorded, and each lands as null
        // rather than as an empty string, which would read as a nickname of no characters.
        // Counted across the whole file: which boards those members are on is the file's business
        // and changes the day somebody records one.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_members WHERE nickname IS NULL AND $ACTIVE",
                Int::class.java,
            ),
        ).isEqualTo(seededMembers.count { it.getValue("nickname").isBlank() })
    }

    @Test
    fun `a place is served for as long as its board sits`() {
        runLoader()

        // The files carry no dates of their own for a member, so the board's own are what a
        // membership starts with -- including the ninth board's real handover date.
        val row = member(9, "Emma Dokter")
        assertThat(row["start_date"].toString()).startsWith("2025-09-01")
        assertThat(row["end_date"].toString()).startsWith("2026-09-16")
    }

    @Test
    fun `a blurb carrying a comma, a quote and a line of its own arrives whole`() {
        runLoader()

        // The one reader across the application, so a quoted blurb cannot parse two ways.
        val blurb = member(9, "Taha Aydin")["description"] as String
        assertThat(blurb).startsWith("Hi my name is Taha 'Talpa' Aydin,")
        assertThat(blurb).contains("\n")
        assertThat(blurb).endsWith("make it a fun year for all of us.")
    }

    @Test
    fun `an edit made on the site outlives the next run`() {
        runLoader()
        jdbc.update("UPDATE boards SET name = 'Something Else', cheer = NULL, end_date = '2025-01-01' WHERE number = 8")
        jdbc.update("UPDATE board_members SET nickname = 'edited', role = 'Chair' WHERE display_name = 'Chris Wong'")

        runLoader()

        // The database is the later record: the files add what it has never had and edit nothing.
        assertThat(board(8)["name"]).isEqualTo("Something Else")
        assertThat(board(8)["cheer"]).isNull()
        assertThat(board(8)["end_date"].toString()).startsWith("2025-01-01")
        assertThat(member(8, "Chris Wong")["nickname"]).isEqualTo("edited")
        assertThat(member(8, "Chris Wong")["role"]).isEqualTo("Chair")
    }

    @Test
    fun `a member renamed on the site is not written again under the name in the file`() {
        runLoader()
        jdbc.update("UPDATE board_members SET display_name = 'Christopher Wong' WHERE display_name = 'Chris Wong'")

        runLoader()

        assertThat(count("board_members")).isEqualTo(seededMembers.size)
        assertThat(membersNamed("Chris Wong")).isZero()
    }

    @Test
    fun `a member removed for good is not written again`() {
        runLoader()
        jdbc.update("DELETE FROM board_members WHERE display_name = 'Louis Hu'")

        runLoader()

        assertThat(membersNamed("Louis Hu")).isZero()
    }

    @Test
    fun `a row the database has never had is added by the next run`() {
        runLoader()
        // What a line appended to the file looks like to a database that has run it before.
        jdbc.update("DELETE FROM board_members WHERE display_name = 'Louis Hu'")
        jdbc.update("DELETE FROM seed_applied WHERE seed = 'boards' AND record_key LIKE 'member|%|Louis Hu'")

        val applied = runLoader()

        assertThat(applied).isEqualTo(ShippedBoards.Applied(boards = 0, members = 1))
        assertThat(membersNamed("Louis Hu")).isEqualTo(1)
    }

    @Test
    fun `a database seeded before the ledger keeps every edit on its first run with it`() {
        runLoader()
        jdbc.update("DELETE FROM seed_applied")
        jdbc.update("UPDATE boards SET cheer = 'edited' WHERE number = 8")

        runLoader()

        assertThat(board(8)["cheer"]).isEqualTo("edited")
        assertThat(count("boards")).isEqualTo(seededBoards.size)
        assertThat(count("board_members")).isEqualTo(seededMembers.size)
    }

    @Test
    fun `a deleted board is left deleted rather than resurrected by the next run`() {
        runLoader()
        jdbc.update("UPDATE boards SET deleted_at = NOW(6) WHERE number = 3")

        runLoader()

        // Its row is still in the file. Removing the row is how a board leaves for good.
        assertThat(count("boards")).isEqualTo(seededBoards.size - 1)
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM boards WHERE number = 3 AND $ACTIVE", Int::class.java),
        ).isZero()
    }

    @Test
    fun `a deleted member is left deleted rather than written again by the next run`() {
        runLoader()
        jdbc.update("UPDATE board_members SET deleted_at = NOW(6) WHERE display_name = 'Louis Hu'")

        runLoader()

        assertThat(count("board_members")).isEqualTo(seededMembers.size - 1)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM board_members WHERE display_name = 'Louis Hu' AND $ACTIVE",
                Int::class.java,
            ),
        ).isZero()
    }

    @Test
    fun `a member is attached to the one account that answers to their name`() {
        val user = named("Roos", "Kruk")

        runLoader()

        // The split name is what makes the match possible: a nickname in quotes matches nobody.
        assertThat(user.fullName).isEqualTo("Roos Kruk")
        assertThat(member(6, "Roos Kruk")["user_id"]).isEqualTo(user.id)
    }

    @Test
    fun `a name nobody answers to leaves the member standing under their own name`() {
        runLoader()

        assertThat(member(1, "Thijs Lieverse")["user_id"]).isNull()
    }

    @Test
    fun `a name two accounts answer to leaves the member standing under their own name`() {
        named("Amber", "Scholtz")
        named("Amber", "Scholtz")

        runLoader()

        // Guessing between two people is worse than leaving it for somebody to resolve.
        assertThat(member(6, "Amber Scholtz")["user_id"]).isNull()
    }

    @Test
    fun `a member detached afterwards is never attached again`() {
        val user = named("Roos", "Kruk")
        runLoader()
        assertThat(member(6, "Roos Kruk")["user_id"]).isEqualTo(user.id)

        jdbc.update("UPDATE board_members SET user_id = NULL WHERE display_name = 'Roos Kruk'")
        runLoader()

        // Detaching somebody says who they are not. A step that re-matched would undo that
        // every time the application came up.
        assertThat(member(6, "Roos Kruk")["user_id"]).isNull()
    }

    private fun number(row: Map<String, String>): Int = row.getValue("number").toInt()

    private fun start(row: Map<String, String>): LocalDate = LocalDate.parse(row.getValue("start_date"))

    private fun end(row: Map<String, String>): LocalDate = LocalDate.parse(row.getValue("end_date"))

    private fun membersOn(number: Int): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM board_members m JOIN boards b ON b.id = m.board_id" +
                " WHERE b.number = ? AND m.$ACTIVE",
            Int::class.java,
            number,
        )!!

    private fun membersNamed(name: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM board_members WHERE display_name = ?", Int::class.java, name)!!

    private fun runLoader(): ShippedBoards.Applied = ShippedBoards(dataSource, transactionTemplate).apply()

    private companion object {
        const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"
    }
}
