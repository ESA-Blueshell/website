package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Gives every publicly readable picture already in storage the widths it is served at.
 *
 * Also repairs: a width is addressed by its source's hash and its own number, so one whose bytes
 * have gone missing is written again to the address it always had, and a lost storage volume
 * fills itself back in on the next start. In the application rather than a migration, which has
 * neither the storage volume nor cwebp, and idempotent, so every start after the first does
 * nothing.
 */
@Component
class StoredImageRenditionsBackfill(
    private val files: FileRepository,
    private val renditions: ImageRenditionWriter,
) {
    @Transactional
    fun run(): Int {
        val kinds = FileType.entries.filter { it.publiclyReadable && it.renditionWidths.isNotEmpty() }
        val sources = files.findSourcesOfTypes(kinds)
        if (sources.isEmpty()) return 0

        val written = sources.sumOf { renditions.derive(it).size }
        log.info("[image-renditions] {} pictures are stored at {} widths in total", sources.size, written)
        return written
    }

    companion object {
        private val log = LoggerFactory.getLogger(StoredImageRenditionsBackfill::class.java)
    }
}

/**
 * Runs the repair once the application is up, and never stops it coming up.
 *
 * A separate bean, so the transaction is opened by the proxy rather than skipped by a call from
 * inside the same object. A failure is reported and swallowed: serving a picture at one width
 * works, and refusing to start because a converter choked on one photograph would take every
 * page down to fix one image's weight.
 */
@Component
class StoredImageRenditionsBackfillOnStartup(
    private val backfill: StoredImageRenditionsBackfill,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            backfill.run()
        } catch (e: Exception) {
            log.warn("[image-renditions] could not store the pictures at their widths: {}", e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StoredImageRenditionsBackfillOnStartup::class.java)
    }
}
