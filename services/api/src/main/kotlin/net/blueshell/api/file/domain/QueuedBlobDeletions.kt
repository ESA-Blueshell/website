package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.BlobToDeleteRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Deletes the bytes a changeset queued in blobs_to_delete, once the application is up.
 *
 * A changeset cannot reach the blob store, so it queues the paths of the rows it deletes and
 * this finishes the job. A path leaves the queue only once its bytes are gone, so one the store
 * refuses is tried again at the next start; nothing here stops the api coming up.
 */
@Component
class QueuedBlobDeletions(
    private val queue: BlobToDeleteRepository,
    private val blobs: BlobStore,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val queued =
            runCatching { queue.findAll() }
                .onFailure { log.warn("[blobs] could not read the deletion queue; it waits for the next start: {}", it.message) }
                .getOrNull()
        if (queued.isNullOrEmpty()) return
        val deleted =
            queued.count { entry ->
                runCatching { blobs.delete(entry.path) }
                    .onSuccess { queue.delete(entry) }
                    .onFailure { log.warn("[blobs] could not delete {}; it stays queued: {}", entry.path, it.message) }
                    .isSuccess
            }
        log.info("[blobs] deleted {} of {} queued blobs", deleted, queued.size)
    }

    private companion object {
        val log = LoggerFactory.getLogger(QueuedBlobDeletions::class.java)
    }
}
