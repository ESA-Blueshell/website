package db.migration

import net.blueshell.api.board.domain.BoardSeed
import net.blueshell.api.shared.seed.SeedCsv
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Date
import java.sql.Types

/**
 * Loads the association's boards from the seed files.
 *
 * The nine boards that have sat and the forty-six seats on them used to be `INSERT` statements
 * inside a versioned migration, which meant that a name nobody knew, a cheer nobody had
 * written down and a fifth seat nobody recorded were each another migration away. The files
 * under `db/seed/boards` are the reviewed record instead: one row per board and one per seat,
 * in a form somebody who was there can read and correct.
 *
 * A board with no seats is a board: the tenth is a candidate board that nobody has taken a
 * seat on yet, and it is a row in `boards.csv` with nothing naming it in `seats.csv`. Whether
 * it is a candidate or in office is read off its dates rather than flagged here.
 *
 * Repeatable rather than versioned, keyed on the files' own contents, so correcting a row is
 * an edit and a deploy rather than another migration. Running it against a database that
 * already agrees with the files changes nothing.
 *
 * Deletion outranks the files. A board or a seat that was soft-deleted stays deleted even
 * while its row is still in the file: somebody removing it is a later decision than the
 * import, and resurrecting it on the next edit anywhere in the file would be a surprise.
 * Removing the row from the file is how it leaves for good.
 *
 * A seat is attached to the account whose name it was recorded under as it is written, and
 * never again. Thirty-one of the forty-six names used to carry a nickname in quotes in the
 * middle of them, which is why no seat before the seventh board has ever been attached to
 * anybody; split apart, the name can be matched. Attribution afterwards is a person's:
 * detaching somebody says who they are not, and a step that re-matched on the next start
 * would undo that every time the application came up.
 *
 * The seat's recorded name is its key here, so the files can correct a role, a nickname, a
 * blurb or a photograph but not a name: a corrected name reads as a seat the files have not
 * seen before. Correcting one is therefore a deploy and a removal of the row it replaces.
 *
 * `image` is written from the files. Those photographs ship with the frontend and are named
 * rather than uploaded, so the files are the record of which one belongs to which board.
 */
@Suppress("unused", "ClassNaming")
class R__Boards_seed : BaseJavaMigration() {

    /**
     * The files are the migration. Flyway re-runs a repeatable migration when its checksum
     * moves, so hashing their contents is what makes an edit take effect.
     */
    override fun getChecksum(): Int = SEED_FILES.fold(11) { acc, name -> 31 * acc + read(name).hashCode() }

    override fun migrate(context: Context) {
        val connection = context.connection
        val boards = parse(read("boards.csv"))
        val seats = parse(read("seats.csv"))

        val boardRows = boards.associateBy { row -> row.getValue("number") }
        val boardIds = boards.associate { row -> row.getValue("number") to upsertBoard(connection, row) }

        var written = 0
        var attached = 0
        var skipped = 0
        seats.forEach { row ->
            val number = row.getValue("board")
            val boardId = boardIds[number]
            val boardRow = boardRows[number]
            if (boardId == null || boardRow == null) {
                // The board it sits on is deleted, or the file names one that has no row at all.
                skipped += 1
                return@forEach
            }
            when (upsertSeat(connection, boardId, boardRow, row)) {
                Seat.ATTACHED -> { written += 1; attached += 1 }
                Seat.WRITTEN -> written += 1
                Seat.LEFT_DELETED -> skipped += 1
            }
        }

        if (attached > 0) log.info("[boards-seed] {} seats found the account they were recorded under", attached)
        log.info(
            "[boards-seed] {} boards and {} seats applied ({} left to their deletion)",
            boardIds.values.count { it != null },
            written,
            skipped,
        )
    }

    /** What became of one seat the file lists. */
    private enum class Seat { ATTACHED, WRITTEN, LEFT_DELETED }

    /**
     * A board as the file has it: its number, the name it chose for itself where it has one,
     * its cheer, its colour, what the year was about, its dates and the photograph naming it.
     *
     * The number is the identity and is never rewritten. `candidate` duplicates the name, is
     * `NOT NULL` and is read by nothing, so it is filled with the name — or with the number,
     * since a board is free to have no name recorded at all.
     */
    private fun upsertBoard(connection: Connection, row: Map<String, String>): Long? {
        val number = row.getValue("number").toInt()
        val find = "SELECT id FROM boards WHERE number = ?"
        val existing = boardNumbered(connection, "$find AND $ACTIVE", number)
        if (existing == null && boardNumbered(connection, "$find AND NOT $ACTIVE", number) != null) return null

        val name = row.getValue("name").ifBlank { null }
        val fields = listOf<Any?>(
            name,
            name ?: "Board $number",
            row.getValue("cheer").ifBlank { null },
            row.getValue("accent").ifBlank { null },
            row.getValue("description").ifBlank { null },
            Date.valueOf(row.getValue("start_date")),
            row.getValue("end_date").ifBlank { null }?.let { Date.valueOf(it) },
            row.getValue("image").ifBlank { null },
        )

        if (existing != null) {
            connection.prepareStatement(
                """
                UPDATE boards
                SET name = ?, candidate = ?, cheer = ?, accent = ?, description = ?,
                    start_date = ?, end_date = ?, image = ?
                WHERE id = ?
                  AND NOT (name <=> ? AND candidate <=> ? AND cheer <=> ? AND accent <=> ?
                           AND description <=> ? AND start_date <=> ? AND end_date <=> ? AND image <=> ?)
                """.trimIndent(),
            ).use { statement ->
                fields.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.setLong(fields.size + 1, existing)
                fields.forEachIndexed { index, value -> statement.setObject(index + fields.size + 2, value) }
                statement.executeUpdate()
            }
            return existing
        }

        connection.prepareStatement(
            """
            INSERT INTO boards (number, name, candidate, cheer, accent, description,
                                start_date, end_date, image)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, number)
            fields.forEachIndexed { index, value -> statement.setObject(index + 2, value) }
            statement.executeUpdate()
        }
        return boardNumbered(connection, "$find AND $ACTIVE", number)
    }

    /**
     * A seat as the file has it, found by the board it sits on and the name it was recorded
     * under, which is what identifies one person's place on one board.
     *
     * The account the seat belongs to is not the file's to set beyond the moment the seat is
     * written, and neither are the dates it was served: a mid-year handover is recorded on the
     * seat rather than in the file, so a seat that already exists keeps the dates it has.
     */
    private fun upsertSeat(
        connection: Connection,
        boardId: Long,
        boardRow: Map<String, String>,
        row: Map<String, String>,
    ): Seat {
        val name = row.getValue("name")
        val find = "SELECT id FROM board_members WHERE board_id = ? AND display_name = ?"
        val fields = listOf<Any?>(
            row.getValue("nickname").ifBlank { null },
            row.getValue("role"),
            row.getValue("description").ifBlank { null },
            row.getValue("image").ifBlank { null },
        )

        val existing = seatOf(connection, "$find AND $ACTIVE", boardId, name)
        if (existing != null) {
            connection.prepareStatement(
                """
                UPDATE board_members
                SET nickname = ?, role = ?, description = ?, image = ?
                WHERE id = ?
                  AND NOT (nickname <=> ? AND role <=> ? AND description <=> ? AND image <=> ?)
                """.trimIndent(),
            ).use { statement ->
                fields.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.setLong(fields.size + 1, existing)
                fields.forEachIndexed { index, value -> statement.setObject(index + fields.size + 2, value) }
                statement.executeUpdate()
            }
            return Seat.WRITTEN
        }
        if (seatOf(connection, "$find AND NOT $ACTIVE", boardId, name) != null) return Seat.LEFT_DELETED

        // Attached as the seat is created, which is the only moment this can be settled without
        // overruling somebody. See the note on attribution in the header.
        val memberId = memberNamed(connection, name)?.takeIf { !alreadySeated(connection, boardId, it) }
        connection.prepareStatement(
            """
            INSERT INTO board_members (board_id, user_id, display_name, nickname, role,
                                       description, image, start_date, end_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, boardId)
            if (memberId == null) statement.setNull(2, Types.BIGINT) else statement.setLong(2, memberId)
            statement.setString(3, name)
            statement.setObject(4, fields[0])
            statement.setObject(5, fields[1])
            statement.setObject(6, fields[2])
            statement.setObject(7, fields[3])
            // A seat is served for as long as its board sits unless somebody says otherwise,
            // and the files carry no dates of their own.
            statement.setDate(8, Date.valueOf(boardRow.getValue("start_date")))
            statement.setObject(9, boardRow.getValue("end_date").ifBlank { null }?.let { Date.valueOf(it) })
            statement.executeUpdate()
        }
        return if (memberId == null) Seat.WRITTEN else Seat.ATTACHED
    }

    /**
     * The one account that answers to a name exactly, or nobody.
     *
     * Built the way the site writes a name, prefix and all, and matched against the seat's own
     * name now that the nickname is beside it rather than in quotes in the middle of it. A name
     * matching nobody, or more than one person, leaves the seat standing under its own name:
     * guessing between two people is worse than leaving it.
     */
    private fun memberNamed(connection: Connection, name: String): Long? =
        connection.prepareStatement(
            """
            SELECT MIN(u.id) FROM users u
            WHERE TRIM(CONCAT_WS(' ', u.first_name, u.prefix, u.last_name)) = ? AND u.$ACTIVE
            HAVING COUNT(*) = 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                val id = rows.getLong(1)
                if (rows.wasNull()) null else id
            }
        }

    /**
     * Whether an account already holds a seat on this board.
     *
     * A board holds one seat per account, so a person seated by hand under no recorded name is
     * not seated a second time by their name in the file.
     */
    private fun alreadySeated(connection: Connection, boardId: Long, userId: Long): Boolean =
        connection.prepareStatement(
            "SELECT id FROM board_members WHERE board_id = ? AND user_id = ? AND $ACTIVE",
        ).use { statement ->
            statement.setLong(1, boardId)
            statement.setLong(2, userId)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun boardNumbered(connection: Connection, sql: String, number: Int): Long? =
        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, number)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun seatOf(connection: Connection, sql: String, boardId: Long, name: String): Long? =
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, boardId)
            statement.setString(2, name)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun read(name: String): String = BoardSeed.files.read(name)

    companion object {
        private val log = LoggerFactory.getLogger(R__Boards_seed::class.java)
        private val SEED_FILES = listOf("boards.csv", "seats.csv")

        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        private const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"

        /**
         * The rows of one seed file.
         *
         * Delegates to [SeedCsv], the one reader across the application, so a quoted blurb
         * cannot parse two ways depending on which seed is looking at it.
         */
        fun parse(content: String): List<Map<String, String>> = SeedCsv.parse(content)
    }
}
