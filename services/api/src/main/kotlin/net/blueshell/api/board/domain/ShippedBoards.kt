package net.blueshell.api.board.domain

import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedLedger
import net.blueshell.api.shared.seed.SeedOrder
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.sql.Date
import java.sql.Types
import javax.sql.DataSource

/**
 * Adds the boards in `db/seed/boards` that the database has never had, and leaves every row it
 * has alone. Each row is written once, on the first start that finds it (see [SeedLedger]), so
 * an edit, rename or deletion made on the site outlives every later start. The art follows separately.
 */
@Component
class ShippedBoards(
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    private val seed: SeedCsv = BoardSeed.files,
) {
    /** The rows a run wrote, which is none at all once the files have been applied. */
    data class Applied(
        val boards: Int,
        val members: Int,
    )

    // One transaction, as the migration had. DataSourceUtils returns the connection it is bound to.
    fun apply(): Applied =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(connection)
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }!!

    private fun load(connection: Connection): Applied {
        val boards = parse(read("boards.csv"))
        val members = parse(read("members.csv"))
        val ledger = SeedLedger(connection, SEED)

        val boardRows = boards.associateBy { row -> row.getValue("number") }
        val boardsAdded = boards.count { row -> addBoard(connection, ledger, row) == Outcome.WRITTEN }
        val boardIds = boards.associate { row -> row.getValue("number").let { it to boardNumbered(connection, it.toInt()) } }

        val outcomes =
            members.map { row ->
                val number = row.getValue("board")
                val boardId = boardIds[number]
                val boardRow = boardRows[number]
                // The board is deleted, or the file names one that has no row of its own.
                if (boardId == null || boardRow == null) {
                    Outcome.KEPT
                } else {
                    addMember(connection, ledger, boardId, boardRow, row)
                }
            }

        val applied =
            Applied(
                boards = boardsAdded,
                members = outcomes.count { it != Outcome.KEPT },
            )
        val attached = outcomes.count { it == Outcome.ATTACHED }
        if (attached > 0) log.info("[boards-seed] {} members found the account they were recorded under", attached)
        if (applied.boards > 0 || applied.members > 0) {
            log.info("[boards-seed] {} boards and {} members added", applied.boards, applied.members)
        }
        return applied
    }

    /** What became of one row the file lists. */
    private enum class Outcome { ATTACHED, WRITTEN, KEPT }

    /**
     * Writes one board the database has never had, keyed on its number.
     * `candidate` is `NOT NULL` and read by nothing, so it is filled with the name or the number.
     */
    private fun addBoard(
        connection: Connection,
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Outcome {
        val number = row.getValue("number").toInt()
        val toWrite =
            ledger.toWrite("board|$number") {
                connection.prepareStatement("SELECT id FROM boards WHERE number = ?").use { statement ->
                    statement.setInt(1, number)
                    statement.executeQuery().use { rows -> rows.next() }
                }
            }
        if (!toWrite) return Outcome.KEPT

        val name = row.getValue("name").ifBlank { null }
        connection
            .prepareStatement(
                """
                INSERT INTO boards (number, name, candidate, cheer, accent, description,
                                    start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                listOf<Any?>(
                    number,
                    name,
                    name ?: "Board $number",
                    row.getValue("cheer").ifBlank { null },
                    row.getValue("accent").ifBlank { null },
                    row.getValue("description").ifBlank { null },
                    Date.valueOf(row.getValue("start_date")),
                    row.getValue("end_date").ifBlank { null }?.let { Date.valueOf(it) },
                ).forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        return Outcome.WRITTEN
    }

    /**
     * Writes one member the database has never had, keyed on the board they sat on and the name
     * recorded for them. Their dates start as the board's own, since the files carry none.
     */
    private fun addMember(
        connection: Connection,
        ledger: SeedLedger,
        boardId: Long,
        boardRow: Map<String, String>,
        row: Map<String, String>,
    ): Outcome {
        val name = row.getValue("name")
        val toWrite =
            ledger.toWrite("member|${boardRow.getValue("number")}|$name") {
                connection
                    .prepareStatement("SELECT id FROM board_members WHERE board_id = ? AND display_name = ?")
                    .use { statement ->
                        statement.setLong(1, boardId)
                        statement.setString(2, name)
                        statement.executeQuery().use { rows -> rows.next() }
                    }
            }
        if (!toWrite) return Outcome.KEPT

        // Attached as the membership is created, which is the only moment this can be settled
        // without overruling somebody.
        val memberId = memberNamed(connection, name)?.takeIf { !alreadyOnBoard(connection, boardId, it) }
        connection
            .prepareStatement(
                """
                INSERT INTO board_members (board_id, user_id, display_name, nickname, role,
                                           description, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, boardId)
                if (memberId == null) statement.setNull(2, Types.BIGINT) else statement.setLong(2, memberId)
                statement.setString(3, name)
                statement.setObject(4, row.getValue("nickname").ifBlank { null })
                statement.setObject(5, row.getValue("role"))
                statement.setObject(6, row.getValue("description").ifBlank { null })
                statement.setDate(7, Date.valueOf(boardRow.getValue("start_date")))
                statement.setObject(8, boardRow.getValue("end_date").ifBlank { null }?.let { Date.valueOf(it) })
                statement.executeUpdate()
            }
        return if (memberId == null) Outcome.WRITTEN else Outcome.ATTACHED
    }

    /**
     * The one account that answers to a name exactly, or nobody.
     *
     * Built the way the site writes a name, prefix and all, and matched against the recorded
     * name now that the nickname is beside it rather than in quotes in the middle of it. A name
     * matching nobody, or more than one person, leaves the member standing under their own name:
     * guessing between two people is worse than leaving it.
     */
    private fun memberNamed(
        connection: Connection,
        name: String,
    ): Long? =
        connection
            .prepareStatement(
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
    private fun alreadyOnBoard(
        connection: Connection,
        boardId: Long,
        userId: Long,
    ): Boolean =
        connection
            .prepareStatement(
                "SELECT id FROM board_members WHERE board_id = ? AND user_id = ? AND $ACTIVE",
            ).use { statement ->
                statement.setLong(1, boardId)
                statement.setLong(2, userId)
                statement.executeQuery().use { rows -> rows.next() }
            }

    private fun boardNumbered(
        connection: Connection,
        number: Int,
    ): Long? =
        connection.prepareStatement("SELECT id FROM boards WHERE number = ? AND $ACTIVE").use { statement ->
            statement.setInt(1, number)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun read(name: String): String = seed.read(name)

    companion object {
        private val log = LoggerFactory.getLogger(ShippedBoards::class.java)

        private const val SEED = "boards"

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

/**
 * A separate bean so the transaction is opened by the proxy, and a failure never blocks start-up.
 */
@Component
class ShippedBoardsOnStartup(
    private val boards: ShippedBoards,
) {
    @Order(SeedOrder.RECORDS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            boards.apply()
        } catch (e: Exception) {
            log.warn("[boards-seed] could not load the boards that ship: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedBoardsOnStartup::class.java)
    }
}
