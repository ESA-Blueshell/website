package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Gives the pictures already in storage the widths they are meant to be served at.
 *
 * Widths are written when a picture is stored, which does nothing for the pictures stored
 * before that was true. Every publicly readable picture is offered its widths here instead, so
 * a poster uploaded last month is on the ladder without anybody re-uploading it.
 *
 * It also repairs: a width is addressed by its source's hash and its own number, so one whose
 * bytes have gone missing is written again to the address it always had. A lost storage volume
 * therefore fills itself back in on the next start rather than becoming a recovery exercise.
 *
 * In the application rather than in a migration, for two reasons: the bytes are the thing being
 * written, and the converter is the thing writing them. A migration runner has neither the
 * storage volume nor cwebp.
 *
 * Idempotent by construction — a width whose record and bytes are both there is left alone — so
 * every start after the first looks and does nothing.
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
 * A separate bean so the transaction is opened by the proxy rather than skipped by a call from
 * inside the same object, the way the dimensions backfill beside it is arranged.
 *
 * A failure is reported and swallowed. Serving a picture at one width is what the site did
 * until this existed, and refusing to start because a converter choked on one photograph would
 * take every page down to fix one image's weight.
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
