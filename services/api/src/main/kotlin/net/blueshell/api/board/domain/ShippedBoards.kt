package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedLedger
import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Adds the boards in `db/seed/boards` that the database has never had, and leaves every row it
 * has alone. Each row is written once, on the first start that finds it (see [SeedLedger]), so
 * an edit, rename or deletion made on the site outlives every later start. The art follows separately.
 *
 * Rows are written through the repositories, so the entity is the one statement of the columns.
 * The ledger stays on the transaction's own connection.
 */
@Component
class ShippedBoards(
    private val boards: BoardRepository,
    private val members: BoardMemberRepository,
    private val users: UserService,
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    private val seed: SeedCsv = BoardSeed.files,
) {
    /** The rows a run wrote, which is none at all once the files have been applied. */
    data class Applied(
        val boards: Int,
        val members: Int,
    )

    // One transaction. DataSourceUtils returns the connection the transaction is bound to.
    fun apply(): Applied =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(SeedLedger(connection, SEED))
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }

    private fun load(ledger: SeedLedger): Applied {
        val boardRows = seed.rows("boards.csv")
        val boardsAdded = boardRows.count { row -> addBoard(ledger, row) }
        val byNumber = boardRows.associateBy { row -> row.getValue("number") }

        val outcomes =
            seed.rows("members.csv").map { row ->
                // The file names a board with no row of its own, or the board is deleted.
                val boardRow = byNumber[row.getValue("board")] ?: return@map Outcome.KEPT
                val board = boards.findByNumber(boardRow.getValue("number").toInt()).orElse(null) ?: return@map Outcome.KEPT
                addMember(ledger, board, boardRow, row)
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
     * Writes one board the database has never had, keyed on its number, and says whether it did.
     * `candidate` is `NOT NULL` and read by nothing, so it is filled with the name or the number.
     */
    private fun addBoard(
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Boolean {
        val number = row.getValue("number").toInt()
        if (!ledger.toWrite("board|$number") { boards.countEverNumbered(number) > 0 }) return false

        val name = row.getValue("name").ifBlank { null }
        boards.save(
            Board(
                number = number,
                candidate = name ?: "Board $number",
                startDate = LocalDate.parse(row.getValue("start_date")),
                name = name,
                endDate = row.getValue("end_date").ifBlank { null }?.let(LocalDate::parse),
                cheer = row.getValue("cheer").ifBlank { null },
                accent = row.getValue("accent").ifBlank { null },
                description = row.getValue("description").ifBlank { null },
            ),
        )
        return true
    }

    /**
     * Writes one member the database has never had, keyed on the board they sat on and the name
     * recorded for them. Their dates start as the board's own, since the files carry none.
     */
    private fun addMember(
        ledger: SeedLedger,
        board: Board,
        boardRow: Map<String, String>,
        row: Map<String, String>,
    ): Outcome {
        val name = row.getValue("name")
        val key = "member|${boardRow.getValue("number")}|$name"
        if (!ledger.toWrite(key) { members.countEverNamedOn(board.id!!, name) > 0 }) return Outcome.KEPT

        // Attached as the membership is created, which is the only moment this can be settled
        // without overruling somebody. A name matching nobody, or more than one person, leaves
        // the member standing under their own name: guessing between two people is worse.
        val account = users.findOnlyByWrittenName(name)?.takeIf { !members.existsByBoardAndUser(board, it) }
        members.save(
            BoardMember(
                board = board,
                user = account,
                role = row.getValue("role"),
                startDate = LocalDate.parse(boardRow.getValue("start_date")),
                endDate = boardRow.getValue("end_date").ifBlank { null }?.let(LocalDate::parse),
                displayName = name,
                nickname = row.getValue("nickname").ifBlank { null },
                description = row.getValue("description").ifBlank { null },
            ),
        )
        return if (account == null) Outcome.WRITTEN else Outcome.ATTACHED
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedBoards::class.java)

        const val SEED = "boards"
    }
}

/**
 * Loads the boards that ship once the application is up. A failure is logged, never thrown, so it
 * cannot block start-up.
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
