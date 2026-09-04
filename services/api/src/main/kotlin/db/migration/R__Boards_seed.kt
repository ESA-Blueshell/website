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
 * Loads the boards and their members from the seed files under `db/seed/boards`.
 *
 * Repeatable and keyed on the files' contents, so correcting a row is an edit and a deploy.
 * Deletion outranks the files: a soft-deleted board or member stays deleted while its row is
 * still listed, and removing the row is how it leaves for good. The recorded name is the key,
 * so the files can correct a role, a nickname, a blurb or a photograph but not a name.
 * A member is attached to the account matching their name once and never re-matched, so
 * detaching somebody stays detached. `photo` and `portrait` are not written here: storing a
 * picture needs the volume and converter a migration runner lacks, so `ShippedBoardArt` fills
 * them once the application is up.
 */
@Suppress("unused", "ClassNaming")
class R__Boards_seed : BaseJavaMigration() {

    /** Hashes the files' contents: Flyway re-runs a repeatable migration when its checksum moves. */
    override fun getChecksum(): Int = SEED_FILES.fold(11) { acc, name -> 31 * acc + read(name).hashCode() }

    override fun migrate(context: Context) {
        val connection = context.connection
        val boards = parse(read("boards.csv"))
        val members = parse(read("members.csv"))

        val boardRows = boards.associateBy { row -> row.getValue("number") }
        val boardIds = boards.associate { row -> row.getValue("number") to upsertBoard(connection, row) }

        val outcomes = members.map { row ->
            val number = row.getValue("board")
            val boardId = boardIds[number]
            val boardRow = boardRows[number]
            // The board is deleted, or the file names one that has no row of its own.
            if (boardId == null || boardRow == null) {
                Member.LEFT_DELETED
            } else {
                upsertMember(connection, boardId, boardRow, row)
            }
        }

        val attached = outcomes.count { it == Member.ATTACHED }
        val written = outcomes.count { it != Member.LEFT_DELETED }
        if (attached > 0) log.info("[boards-seed] {} members found the account they were recorded under", attached)
        log.info(
            "[boards-seed] {} boards and {} members applied ({} left to their deletion)",
            boardIds.values.count { it != null },
            written,
            outcomes.size - written,
        )
    }

    /** What became of one member the file lists. */
    private enum class Member { ATTACHED, WRITTEN, LEFT_DELETED }

    /**
     * Upserts one board, keyed on its number, which is the identity and is never rewritten.
     * `candidate` is `NOT NULL` and read by nothing, so it is filled with the name or the number.
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
     * A member as the file has it, found by the board they sat on and the name recorded for
     * them, which is what identifies one person's place on one board.
     *
     * The account a membership belongs to is not the file's to set beyond the moment it is
     * written, and neither are the dates it was served: a mid-year handover is recorded on the
     * membership rather than in the file, so one that already exists keeps the dates it has.
     */
    private fun upsertMember(
        connection: Connection,
        boardId: Long,
        boardRow: Map<String, String>,
        row: Map<String, String>,
    ): Member {
        val name = row.getValue("name")
        val find = "SELECT id FROM board_members WHERE board_id = ? AND display_name = ?"
        val fields = listOf<Any?>(
            row.getValue("nickname").ifBlank { null },
            row.getValue("role"),
            row.getValue("description").ifBlank { null },
            row.getValue("image").ifBlank { null },
        )

        val existing = memberOf(connection, "$find AND $ACTIVE", boardId, name)
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
            return Member.WRITTEN
        }
        if (memberOf(connection, "$find AND NOT $ACTIVE", boardId, name) != null) return Member.LEFT_DELETED

        // Attached as the membership is created, which is the only moment this can be settled
        // without overruling somebody. See the note on attribution in the header.
        val memberId = memberNamed(connection, name)?.takeIf { !alreadyOnBoard(connection, boardId, it) }
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
            // A place is served for as long as its board sits unless somebody says otherwise,
            // and the files carry no dates of their own.
            statement.setDate(8, Date.valueOf(boardRow.getValue("start_date")))
            statement.setObject(9, boardRow.getValue("end_date").ifBlank { null }?.let { Date.valueOf(it) })
            statement.executeUpdate()
        }
        return if (memberId == null) Member.WRITTEN else Member.ATTACHED
    }

    /**
     * The one account that answers to a name exactly, or nobody.
     *
     * Built the way the site writes a name, prefix and all, and matched against the recorded
     * name now that the nickname is beside it rather than in quotes in the middle of it. A name
     * matching nobody, or more than one person, leaves the member standing under their own name:
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
     * Whether an account is already on this board.
     *
     * A board holds one membership per account, so a person added by hand under no recorded
     * name is not added a second time by their name in the file.
     */
    private fun alreadyOnBoard(connection: Connection, boardId: Long, userId: Long): Boolean =
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

    private fun memberOf(connection: Connection, sql: String, boardId: Long, name: String): Long? =
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, boardId)
            statement.setString(2, name)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun read(name: String): String = BoardSeed.files.read(name)

    companion object {
        private val log = LoggerFactory.getLogger(R__Boards_seed::class.java)
        private val SEED_FILES = listOf("boards.csv", "members.csv")

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
