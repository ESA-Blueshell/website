package net.blueshell.api.event.domain

import net.blueshell.api.event.api.AnnounceMorning
import net.blueshell.api.event.persistence.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Gives an approved event that has no announce at the first 08:00 after the start.
 *
 * Approving sets one, so only events approved before the board chose when their events-info
 * post goes out lack it. Their posts go out together at that morning, one burst the rollout
 * accepts. An event whose post is already out gets one too, which changes nothing for it.
 */
@Component
class AnnounceAtBackfill(
    private val events: EventRepository,
) {
    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onReady() {
        val now = clock.instant()
        val given = events.announceUnannouncedAt(AnnounceMorning.next(now), now)
        if (given > 0) log.info("[announce-at] gave {} approved events the next 08:00", given)
    }

    private companion object {
        val log = LoggerFactory.getLogger(AnnounceAtBackfill::class.java)
    }
}
