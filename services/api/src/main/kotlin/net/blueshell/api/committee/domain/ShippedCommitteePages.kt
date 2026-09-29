package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.game.api.ShippedGames
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
import javax.sql.DataSource

/**
 * Says once, from `db/seed/committees/pages.csv`, which games each committee organises events
 * for. Each fact is written once, on the first start that finds both the
 * committee and the game standing (see [SeedLedger]), so the board's later edits outlive it.
 */
@Component
class ShippedCommitteePages(
    private val committees: CommitteeRepository,
    private val games: ShippedGames,
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    private val seed: SeedCsv = SeedCsv("db/seed/committees"),
) {
    /** How many facts a run wrote, which is none once the file has been applied. */
    fun apply(): Int =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(SeedLedger(connection, SEED))
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }

    private fun load(ledger: SeedLedger): Int =
        seed.rows("pages.csv").sumOf { row ->
            val name = row.getValue("name")
            val committee = committees.findByName(name) ?: return@sumOf 0
            var written = 0
            row.getValue("games").split(' ').filter { it.isNotBlank() }.forEach { code ->
                if (games.stands(code) && ledger.toWrite("committee-game|$name|$code") { code in committee.gameCodes }) {
                    committee.gameCodes.add(code)
                    written++
                }
            }
            written
        }

    private companion object {
        const val SEED = "committees"
    }
}

/**
 * Loads the committee pages that ship once the application is up. A failure is logged, never thrown, so it
 * cannot block start-up.
 */
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
