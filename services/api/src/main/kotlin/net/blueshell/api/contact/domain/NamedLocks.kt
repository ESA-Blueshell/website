package net.blueshell.api.contact.domain

import org.springframework.stereotype.Component
import javax.sql.DataSource

/** A lock every replica of the api sees, by name, for work two pods must not do at once. */
interface NamedLocks {
    /** Runs [block] holding the lock called [name], waiting for another holder to let it go. */
    fun <T> holding(
        name: String,
        block: () -> T,
    ): T
}

/** The lock was held by another replica for longer than anybody should wait. */
class NamedLockTimeout(
    name: String,
) : IllegalStateException("Another replica held the lock '$name' too long")

/**
 * MariaDB's named lock, on a connection of its own held for the block: the lock belongs to the
 * connection, so the caller's transaction neither carries it nor ends it.
 */
@Component
class MariaDbNamedLocks(
    private val dataSource: DataSource,
) : NamedLocks {
    override fun <T> holding(
        name: String,
        block: () -> T,
    ): T =
        dataSource.connection.use { connection ->
            // MariaDB refuses a lock name over 64 characters.
            val key = name.take(MAX_NAME)
            val got =
                connection.prepareStatement("SELECT GET_LOCK(?, ?)").use { statement ->
                    statement.setString(1, key)
                    statement.setInt(2, WAIT_SECONDS)
                    statement.executeQuery().use { it.next() && it.getInt(1) == 1 }
                }
            if (!got) throw NamedLockTimeout(key)
            try {
                block()
            } finally {
                connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                    statement.setString(1, key)
                    statement.execute()
                }
            }
        }

    private companion object {
        const val MAX_NAME = 64
        const val WAIT_SECONDS = 30
    }
}
