package net.blueshell.api.sync.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Which of the bot's posts should stand for an event at a moment. [announce] governs the
 * events-info post, [calendarPost] the events-calendar post, which comes down for good once
 * [calendarPostOver]; the Discord event reads only [over].
 */
data class DiscordPostsDue(
    val announce: Boolean,
    val calendarPost: Boolean,
    val calendarPostOver: Boolean,
    val over: Boolean,
)

/**
 * When the bot's posts for an event are due, in Amsterdam time: the events-info post from the
 * announce at the board chose until the event is over, or at once where the event starts before
 * it; the events-calendar post from 08:00 on the event's first day until 08:00 the morning after
 * its last.
 */
object DiscordPostSchedule {
    const val ZONE_ID = "Europe/Amsterdam"
    val ZONE: ZoneId = ZoneId.of(ZONE_ID)
    private val MORNING: LocalTime = LocalTime.of(8, 0)

    fun calendarPostFrom(start: Instant): Instant = morningOf(start, 0)

    fun due(
        start: Instant,
        end: Instant,
        announceAt: Instant?,
        now: Instant,
    ): DiscordPostsDue {
        val over = !now.isBefore(end)
        val calendarPostOver = !now.isBefore(takeDownAt(end))
        return DiscordPostsDue(
            announce = !over && announceAt != null && (!now.isBefore(announceAt) || !start.isAfter(announceAt)),
            calendarPost = !now.isBefore(calendarPostFrom(start)) && !calendarPostOver,
            calendarPostOver = calendarPostOver,
            over = over,
        )
    }

    // The first 08:00 at or after the end: an event over at 02:00 comes down that same morning.
    private fun takeDownAt(end: Instant): Instant = morningOf(end, 0).takeIf { !end.isAfter(it) } ?: morningOf(end, 1)

    private fun morningOf(
        moment: Instant,
        daysLater: Long,
    ): Instant =
        moment
            .atZone(ZONE)
            .toLocalDate()
            .plusDays(daysLater)
            .atTime(MORNING)
            .atZone(ZONE)
            .toInstant()
}
