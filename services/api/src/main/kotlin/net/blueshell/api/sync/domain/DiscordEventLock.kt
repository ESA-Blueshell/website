package net.blueshell.api.sync.domain

import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import org.springframework.stereotype.Component

/**
 * Runs one event's Discord work at a time, across runs and pods: a job queued while another for
 * the same event runs waits for it, and so reads the event after the change that queued it. Held
 * on the job's own transaction, which it must run inside.
 */
@Component
class DiscordEventLock(
    private val repository: ExternalIdMappingRepository,
) {
    fun <T> holding(
        eventId: Long,
        work: () -> T,
    ): T {
        val name = "discord-event-$eventId"
        check(repository.acquireNamedLock(name, WAIT_SECONDS) == 1) { "Event $eventId's Discord work is still running elsewhere" }
        try {
            return work()
        } finally {
            repository.releaseNamedLock(name)
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60
    }
}
