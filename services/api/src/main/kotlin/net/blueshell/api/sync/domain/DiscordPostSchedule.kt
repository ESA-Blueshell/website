package net.blueshell.api.sync.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Why the bot looks at an event: its morning run, or the event having just changed. */
enum class Trigger { MORNING, CHANGE }

/** What should stand for an event at a moment: its events-info post, its events-calendar post, and whether it is over. */
data class DiscordPostsDue(
    val announce: Boolean,
    val dayPost: Boolean,
    val over: Boolean,
)

/**
 * When the bot's posts about an event are due, in Amsterdam time. The events-info post and the
 * Discord event go out at 08:00 two weeks before the event's first day; the events-calendar post
 * stands from 08:00 on that day until 08:00 the morning after its last.
 *
 * A late event waits for the next morning run, except on or after its own first day, where waiting
 * would miss it: a change then puts up whatever is due at once.
 */
object DiscordPostSchedule {
    val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    val MORNING: LocalTime = LocalTime.of(8, 0)
    private const val LEAD_DAYS = 14L

    fun announceAt(start: Instant): Instant = morningOf(start, -LEAD_DAYS)

    fun dayPostFrom(start: Instant): Instant = morningOf(start, 0)

    fun dayPostUntil(end: Instant): Instant = morningOf(end, 1)

    fun due(
        start: Instant,
        end: Instant,
        live: Boolean,
        now: Instant,
        trigger: Trigger,
    ): DiscordPostsDue {
        val over = !now.isBefore(end)
        val dayHasCome = !now.isBefore(dayPostFrom(start))
        val announce =
            live && !over &&
                when (trigger) {
                    Trigger.MORNING -> !now.isBefore(announceAt(start))
                    Trigger.CHANGE -> dayHasCome
                }
        val dayPost = live && dayHasCome && now.isBefore(dayPostUntil(end))
        return DiscordPostsDue(announce = announce, dayPost = dayPost, over = over)
    }

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
