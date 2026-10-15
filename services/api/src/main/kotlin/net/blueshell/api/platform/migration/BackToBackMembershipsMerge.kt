package net.blueshell.api.platform.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.change.custom.CustomTaskRollback
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate

private const val LIVE = "9999-12-31 23:59:59"
private const val SELECT = "SELECT id, user_id, start_date, end_date, type, activated_on, deleted_at FROM memberships"
private const val BACKUP =
    "INSERT INTO membership_rewrites (membership_id, end_date, type, activated_on, deleted_at) VALUES (?, ?, ?, ?, ?)"
private const val INSERT_TYPE = "INSERT INTO membership_types (membership_id, type, since) VALUES (?, ?, ?)"

private class Row(
    val id: Long,
    val userId: Long,
    val start: LocalDate,
    val end: LocalDate?,
    val type: String,
    val activatedOn: LocalDate?,
    val deletedAt: Timestamp,
) {
    val live: Boolean get() = deletedAt == Timestamp.valueOf(LIVE)
}

/**
 * A membership becomes one unbroken spell with a dated type inside it (api ADR-040). Memberships
 * of one person that run end to start become the first of them, with a type from each old start;
 * a NONE type becomes REGULAR. Each row is kept as it was in `membership_rewrites`, and a rollback
 * writes those back.
 */
class BackToBackMembershipsMerge :
    CustomTaskChange,
    CustomTaskRollback {
    private var merged = 0
    private var kept = 0
    private var retyped = 0

    override fun execute(database: Database) {
        val connection = connectionOf(database)
        val rows = read(connection)
        retyped = rows.count { it.type == "NONE" }
        val rewritten = mutableSetOf<Long>()
        val spells =
            rows
                .filter { it.live }
                .groupBy { it.userId }
                .values
                .flatMap(::chains)
        for (chain in spells) {
            if (chain.size > 1) {
                merge(connection, chain)
                chain.forEach { rewritten += it.id }
                merged += chain.size
                kept++
            }
            insertTypes(connection, chain.first().id, chain)
        }
        for (row in rows.filter { !it.live }) insertTypes(connection, row.id, listOf(row))
        for (row in rows.filter { it.type == "NONE" && it.id !in rewritten }) {
            rewrite(connection, row, row.end, row.activatedOn)
        }
    }

    private fun read(connection: Connection): List<Row> =
        connection.prepareStatement(SELECT).use { select ->
            select.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            Row(
                                id = rows.getLong("id"),
                                userId = rows.getLong("user_id"),
                                start = rows.getDate("start_date").toLocalDate(),
                                end = rows.getDate("end_date")?.toLocalDate(),
                                type = rows.getString("type"),
                                activatedOn = rows.getDate("activated_on")?.toLocalDate(),
                                deletedAt = rows.getTimestamp("deleted_at"),
                            ),
                        )
                    }
                }
            }
        }

    /** One person's memberships, split where a membership does not end on the day the next starts. */
    private fun chains(held: List<Row>): List<List<Row>> =
        held.sortedBy { it.start }.fold(mutableListOf<MutableList<Row>>()) { chains, row ->
            val last = chains.lastOrNull()?.last()
            if (last != null && last.end == row.start) chains.last() += row else chains += mutableListOf(row)
            chains
        }

    private fun merge(
        connection: Connection,
        chain: List<Row>,
    ) {
        // One unbroken spell is active from its first activation, even where a later row was still pending.
        val activatedOn = chain.mapNotNull { it.activatedOn }.minOrNull()
        rewrite(connection, chain.first(), chain.last().end, activatedOn, type = typeOf(chain.last()))
        for (absorbed in chain.drop(1)) rewrite(connection, absorbed, absorbed.end, absorbed.activatedOn, absorb = true)
    }

    // The backup goes first, so a rollback finds every row this rewrote.
    private fun rewrite(
        connection: Connection,
        row: Row,
        end: LocalDate?,
        activatedOn: LocalDate?,
        type: String = typeOf(row),
        absorb: Boolean = false,
    ) {
        connection.write(
            BACKUP,
            row.id,
            row.end?.let(Date::valueOf),
            row.type,
            row.activatedOn?.let(Date::valueOf),
            row.deletedAt,
        )
        val deletedAt = if (absorb) Timestamp(System.currentTimeMillis()) else row.deletedAt
        connection.write(
            "UPDATE memberships SET end_date = ?, type = ?, activated_on = ?, deleted_at = ?, version = version + 1 WHERE id = ?",
            end?.let(Date::valueOf),
            type,
            activatedOn?.let(Date::valueOf),
            deletedAt,
            row.id,
        )
    }

    /** A type from each old start, skipping one that only repeats the type before it. */
    private fun insertTypes(
        connection: Connection,
        membershipId: Long,
        chain: List<Row>,
    ) {
        var previous: String? = null
        for (row in chain) {
            val type = typeOf(row)
            if (type == previous) continue
            connection.write(INSERT_TYPE, membershipId, type, Date.valueOf(row.start))
            previous = type
        }
    }

    private fun typeOf(row: Row) = if (row.type == "NONE") "REGULAR" else row.type

    override fun rollback(database: Database) {
        connectionOf(database).write(
            "UPDATE memberships SET " +
                "end_date = (SELECT r.end_date FROM membership_rewrites r WHERE r.membership_id = memberships.id), " +
                "type = (SELECT r.type FROM membership_rewrites r WHERE r.membership_id = memberships.id), " +
                "activated_on = (SELECT r.activated_on FROM membership_rewrites r WHERE r.membership_id = memberships.id), " +
                "deleted_at = (SELECT r.deleted_at FROM membership_rewrites r WHERE r.membership_id = memberships.id), " +
                "version = version + 1 " +
                "WHERE id IN (SELECT membership_id FROM membership_rewrites)",
        )
    }

    override fun getConfirmationMessage(): String = "Merged $merged back-to-back memberships into $kept; NONE became REGULAR on $retyped"

    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    private fun connectionOf(database: Database): Connection = (database.connection as JdbcConnection).underlyingConnection

    private fun Connection.write(
        sql: String,
        vararg values: Any?,
    ) = prepareStatement(sql).use { statement ->
        values.forEachIndexed { at, value -> statement.setObject(at + 1, value) }
        statement.executeUpdate()
    }
}
