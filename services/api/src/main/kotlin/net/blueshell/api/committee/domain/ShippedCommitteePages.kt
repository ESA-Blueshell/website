package net.blueshell.api.committee.domain

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
import javax.sql.DataSource

/**
 * Says once, from `db/seed/committees/pages.csv`, which committees are unlisted and which games
 * each organises events for. Each fact is written once, on the first start that finds both the
 * committee and the game standing (see [SeedLedger]), so the board's later edits outlive it.
 */
@Component
class ShippedCommitteePages(
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    private val seed: SeedCsv = SeedCsv("db/seed/committees"),
) {
    /** How many facts a run wrote, which is none once the file has been applied. */
    fun apply(): Int =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(connection)
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        } ?: 0

    private fun load(connection: Connection): Int {
        val ledger = SeedLedger(connection, SEED)
        return seed.rows("pages.csv").sumOf { row ->
            val name = row.getValue("name")
            val id = committeeId(connection, name) ?: return@sumOf 0
            var written = 0
            if (!row.getValue("listed").toBoolean() && ledger.toWrite("committee-unlisted|$name") { false }) {
                written += update(connection, "UPDATE committees SET listed = FALSE WHERE id = ?", id)
            }
            row.getValue("games").split(' ').filter { it.isNotBlank() }.forEach { code ->
                if (gameStands(connection, code) && ledger.toWrite("committee-game|$name|$code") { names(connection, id, code) }) {
                    written += link(connection, id, code)
                }
            }
            written
        }
    }

    private fun committeeId(
        connection: Connection,
        name: String,
    ): Long? =
        connection.prepareStatement("SELECT id FROM committees WHERE name = ? AND $ACTIVE").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun gameStands(
        connection: Connection,
        code: String,
    ): Boolean =
        connection.prepareStatement("SELECT 1 FROM game WHERE code = ? AND $ACTIVE").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun names(
        connection: Connection,
        id: Long,
        code: String,
    ): Boolean =
        connection.prepareStatement("SELECT 1 FROM committee_games WHERE committee_id = ? AND game_code = ?").use { statement ->
            statement.setLong(1, id)
            statement.setString(2, code)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun link(
        connection: Connection,
        id: Long,
        code: String,
    ): Int =
        connection.prepareStatement("INSERT INTO committee_games (committee_id, game_code) VALUES (?, ?)").use { statement ->
            statement.setLong(1, id)
            statement.setString(2, code)
            statement.executeUpdate()
        }

    private fun update(
        connection: Connection,
        sql: String,
        id: Long,
    ): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }

    private companion object {
        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"
        const val SEED = "committees"
    }
}

/** A separate bean so the transaction is opened by the proxy, and a failure never blocks start-up. */
@Component
class ShippedCommitteePagesOnStartup(
    private val pages: ShippedCommitteePages,
) {
    @Order(SeedOrder.LINKS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            val written = pages.apply()
            if (written > 0) log.info("[committee-seed] {} committee facts written", written)
        } catch (e: Exception) {
            log.warn("[committee-seed] could not load the committee pages that ship: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedCommitteePagesOnStartup::class.java)
    }
}
