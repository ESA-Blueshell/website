package net.blueshell.api.file.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Stores the pictures a seed folder ships and puts each on the record its row names.
 *
 * Runs on start rather than in the migration that loads the same rows: storing a picture needs
 * the volume and the converter a migration runner lacks. Every picture is stored on every start
 * even when nothing is waiting for it, which is what lets a lost storage volume repair itself,
 * and each is credited to the site's own account. A row that fails is logged and skipped rather
 * than failing the start: its record keeps the picture it had yesterday.
 */
@Component
class ShippedPictures(
    private val files: FileService,
    private val users: UserService,
    private val transactions: TransactionTemplate,
) {
    /**
     * Stores the picture [column] names on each row of [csv] in [seed], then offers it to [place]
     * with its row, and answers for how many rows [place] took it.
     *
     * [place] runs in a transaction of its own per row. It puts the picture on a record whose slot
     * is empty and answers whether it did; a filled slot is somebody's later choice and is left
     * alone. A row whose [column] is blank ships nothing.
     */
    fun ship(
        seed: SeedCsv,
        csv: String,
        column: String,
        kind: FileType,
        place: (row: Map<String, String>, picture: () -> File) -> Boolean,
    ): Int =
        attempt("$csv in ${seed.directory}") {
            val owner = siteAccount() ?: return@attempt 0
            val rows = seed.rows(csv).mapNotNull { row -> row[column]?.ifBlank { null }?.let { art -> row to art } }
            // Every picture first, so one that is waiting for nothing is still put back.
            val stored = mutableMapOf<String, String>()
            rows.forEach { (_, art) -> attempt(art) { store(seed, art, kind, owner, stored).let { 0 } } }
            val placed =
                rows.sumOf { (row, art) ->
                    attempt(art) {
                        if (transactions.execute { place(row) { store(seed, art, kind, owner, stored) } } == true) 1 else 0
                    }
                }
            if (placed > 0) log.info("[shipped-pictures] {} {} pictures now come from {}", placed, kind, seed.directory)
            placed
        }

    /**
     * One picture, stored the first time it is asked for.
     *
     * The address rather than the row is kept between rows, because each row is placed in a
     * transaction of its own and a row read in one is stale in the next. The address is the
     * picture's own contents, so a second row naming the picture reads back the row the first
     * one wrote instead of storing the bytes again.
     */
    private fun store(
        seed: SeedCsv,
        art: String,
        kind: FileType,
        owner: User,
        stored: MutableMap<String, String>,
    ): File {
        stored[art]?.let { path -> files.findPublicImage(path, kind)?.let { return it } }
        val name = "$art.webp"
        val resource = "${seed.directory}/art/$name"
        val bytes = javaClass.classLoader.getResourceAsStream(resource) ?: error("Shipped art $resource is missing")
        val file = files.store(bytes, name, WEBP, kind, owner)
        stored[art] = file.path
        return file
    }

    /**
     * The account the shipped art is credited to. Absent only where the migration that writes it
     * has not run, so it is answered with null rather than thrown.
     */
    private fun siteAccount(): User? {
        val account = runCatching { users.findByUsername(SITE_ACCOUNT) }.getOrNull()
        if (account == null) log.warn("[shipped-pictures] there is no '{}' account to credit the art to", SITE_ACCOUNT)
        return account
    }

    private fun attempt(
        what: String,
        apply: () -> Int,
    ): Int =
        try {
            apply()
        } catch (e: Exception) {
            log.warn("[shipped-pictures] could not place {}: {}", what, e.message)
            0
        }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedPictures::class.java)
        const val WEBP = "image/webp"
        const val SITE_ACCOUNT = "system"
    }
}
