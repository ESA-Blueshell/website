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
 * Measures the pictures already in storage, so every picture has a size and not only those
 * uploaded since sizes were recorded.
 *
 * In the application rather than a migration, which runs where the schema is rather than where
 * the volume is mounted. Idempotent, asking only for pictures with no width; one whose bytes
 * are missing or unreadable is reported and left alone, then offered again next start.
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
 * A separate bean, so the transaction is opened by the proxy rather than skipped by a call from
 * inside the same object. A failure is reported and swallowed: this is a repair rather than a
 * precondition for serving, every caller already handles a missing size, and refusing to start
 * over one would take the site down to fix a picture's layout.
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
