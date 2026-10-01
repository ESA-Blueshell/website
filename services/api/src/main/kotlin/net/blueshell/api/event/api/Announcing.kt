package net.blueshell.api.event.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** When the board says an approved event's events-info post goes out. */
@Schema(enumAsRef = true)
enum class AnnounceChoice {
    NOW,
    NEXT_MORNING,
}

/** Whether an event's events-info post is out, answered by the module that posts it. */
fun interface AnnouncementLedger {
    fun announced(eventId: Long): Boolean
}

/** The morning an events-info post may wait for: 08:00, Amsterdam time. */
object AnnounceMorning {
    val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    private val AT: LocalTime = LocalTime.of(8, 0)

    /** Today's 08:00 while it is still before it, tomorrow's from 08:00 on. */
    fun next(now: Instant): Instant {
        val today = now.atZone(ZONE).toLocalDate()
        val morning = today.atTime(AT).atZone(ZONE).toInstant()
        return if (now.isBefore(morning)) {
            morning
        } else {
            today
                .plusDays(1)
                .atTime(AT)
                .atZone(ZONE)
                .toInstant()
        }
    }
}
