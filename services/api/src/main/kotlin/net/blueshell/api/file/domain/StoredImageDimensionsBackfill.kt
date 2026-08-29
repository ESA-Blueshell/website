package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.FileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Records how large the pictures already in storage are.
 *
 * Sizes are recorded when a picture is stored, which does nothing for the pictures stored
 * before that was true. This measures those, once, so every picture has a size rather than
 * only the ones uploaded since.
 *
 * It runs in the application rather than in a migration, because the bytes are the thing being
 * read: a migration runs where the schema is, which is not necessarily where the storage
 * volume is mounted.
 *
 * Idempotent by construction — it asks for pictures with no width, so a second start finds
 * nothing to do. A picture whose bytes are missing or whose format cannot be read is left
 * alone and reported, not deleted and not refused; it is offered again on the next start,
 * which costs one header read.
 */
@Component
class StoredImageDimensionsBackfill(
    private val files: FileRepository,
    @Value($$"${storage.location}") storageLocation: String,
) {
    private val root: Path = Paths.get(storageLocation)

    @Transactional
    fun run(): Int {
        val pending = files.findImagesMissingDimensions()
        if (pending.isEmpty()) return 0

        var measured = 0
        var unreadable = 0
        pending.forEach { file ->
            val size = ImageDimensions.of(root.resolve(file.path).normalize())
            if (size == null) {
                unreadable += 1
            } else {
                file.width = size.width
                file.height = size.height
                measured += 1
            }
        }
        log.info(
            "[image-dimensions] measured {} of {} stored pictures ({} could not be read)",
            measured,
            pending.size,
            unreadable,
        )
        return measured
    }

    companion object {
        private val log = LoggerFactory.getLogger(StoredImageDimensionsBackfill::class.java)
    }
}

/**
 * Runs the backfill once the application is up, and never stops it coming up.
 *
 * A separate bean rather than a listener on the backfill itself, so the transaction the work
 * needs is opened by the proxy rather than skipped by a call from inside the same object.
 *
 * A failure here is reported and swallowed on purpose. Measuring pictures that were stored
 * before sizes were recorded is a repair, not a precondition for serving: a size that is
 * missing is already something every caller handles, and refusing to start over one would
 * take the whole site down to fix a picture's layout.
 */
@Component
class StoredImageDimensionsBackfillOnStartup(
    private val backfill: StoredImageDimensionsBackfill,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            backfill.run()
        } catch (e: Exception) {
            log.warn("[image-dimensions] could not measure the stored pictures: {}", e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StoredImageDimensionsBackfillOnStartup::class.java)
    }
}
