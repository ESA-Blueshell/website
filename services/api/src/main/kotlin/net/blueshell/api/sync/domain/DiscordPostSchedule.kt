package net.blueshell.api.sync.domain

import net.blueshell.api.shared.job.DiscordPostTrigger
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Which of the bot's things should stand for an event at a moment. */
data class DiscordPostsDue(
    val infoPost: Boolean,
    val calendarPost: Boolean,
    val over: Boolean,
)

/**
 * When the bot's posts about an event are due, in Amsterdam time: the events-info post and the
 * Discord event at 08:00 two weeks before the event's first day, the events-calendar post from
 * 08:00 on that day until 08:00 the morning after its last.
 */
object DiscordPostSchedule {
    const val ZONE_ID = "Europe/Amsterdam"
    val ZONE: ZoneId = ZoneId.of(ZONE_ID)
    private val MORNING: LocalTime = LocalTime.of(8, 0)
    private const val LEAD_DAYS = 14L

    fun infoPostAt(start: Instant): Instant = morningOf(start, -LEAD_DAYS)

    fun due(
        start: Instant,
        end: Instant,
        now: Instant,
        trigger: DiscordPostTrigger,
    ): DiscordPostsDue {
        val over = !now.isBefore(end)
        val firstDayHasCome = !now.isBefore(morningOf(start, 0))
        val infoPost =
            !over &&
                when (trigger) {
                    DiscordPostTrigger.MORNING -> !now.isBefore(infoPostAt(start))
                    DiscordPostTrigger.CHANGE -> firstDayHasCome
                }
        val calendarPost = firstDayHasCome && now.isBefore(morningOf(end, 1))
        return DiscordPostsDue(infoPost = infoPost, calendarPost = calendarPost, over = over)
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
