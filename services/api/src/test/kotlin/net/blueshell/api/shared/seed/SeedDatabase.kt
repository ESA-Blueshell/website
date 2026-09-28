package net.blueshell.api.shared.seed

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * An in-memory database holding the seed ledger's one table, so the loaders run in the unit suite
 * against repositories that keep what they are given. The integration suite checks them against
 * the real schema.
 */
class SeedDatabase {
    val dataSource =
        DriverManagerDataSource(
            "jdbc:h2:mem:seed-${UUID.randomUUID()};MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        )
    val jdbc = JdbcTemplate(dataSource)
    val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))

    init {
        jdbc.execute(SCHEMA)
    }

    fun count(
        sql: String,
        vararg args: Any,
    ): Int = jdbc.queryForObject(sql, Int::class.java, *args)!!

    private companion object {
        val SCHEMA =
            """
            CREATE TABLE seed_applied (
                seed VARCHAR(32) NOT NULL, record_key VARCHAR(512) NOT NULL,
                applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (seed, record_key))
            """.trimIndent()
    }
}
