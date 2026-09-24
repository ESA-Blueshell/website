package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.api.sync.domain.DiscordPostSchedule.infoPostAt
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

/**
 * Brings each of the bot's three things for one event to what should stand now. Safe to run any
 * number of times: before making one it looks in the server for one that links the event and
 * takes that over, and any other copy it finds there is removed, so a copy Discord made after an
 * answer that never arrived is neither made again nor left behind. Creating runs under a claim,
 * and an edit goes out only when the content differs from the fingerprint recorded with it.
 * Whether a late events-info post waits for the morning run is the queueing side's call.
 */
@Service
class DiscordEventPosts(
    private val events: EventPosts,
    private val publisher: ObjectProvider<DiscordPublisher>,
    private val ledger: PostLedger,
    @Value($$"${frontend.url}") private val site: String,
    @Value($$"${discord.posts.info-channel:events-info}") private val infoChannel: String,
    @Value($$"${discord.posts.calendar-channel:events-calendar}") private val calendarChannel: String,
) {
    /* Settable for tests only. */
    internal var clock: Clock = Clock.systemUTC()

    /** The events-info post, which stays once out. A forced run posts it ahead of its morning. */
    fun keepAnnouncement(
        eventId: Long,
        forced: Boolean = false,
    ): Kept =
        keepPost(eventId, DiscordArtefact.INFO_POST, infoChannel) { event, due, out ->
            when {
                out -> null
                due.over -> OVER
                forced -> null
                due.startedBeforeToday -> "The event started before today, so no #$infoChannel announcement is made for it."
                !due.infoPost -> "The #$infoChannel announcement is not due until ${morningOf(infoPostAt(event.startTime))}."
                else -> null
            }
        }

    /**
     * The events-calendar post, up only while the event's day lasts, and only made on its first day.
     * A forced run posts it before that day too, and the next run that is not forced takes it down.
     */
    fun keepCalendarPost(
        eventId: Long,
        forced: Boolean = false,
    ): Kept =
        keepPost(eventId, DiscordArtefact.CALENDAR_POST, calendarChannel) { _, due, out ->
            when {
                due.calendarPost && (out || forced || !due.startedBeforeToday) -> null
                due.calendarPost -> "The event started before today, so no #$calendarChannel post is made for it."
                due.firstDayHasCome -> "The event's day is over, so its #$calendarChannel post has come down."
                forced -> null
                else -> "The #$calendarChannel post is not due until 08:00 on the event's first day."
            }
        }

    /**
     * The Discord event: beside the events-info post, including after an attempt that failed,
     * and gone once the event is over. Made only before the event starts, which Discord insists on;
     * a forced run makes it without waiting for the events-info post.
     */
    fun keepDiscordEvent(
        eventId: Long,
        forced: Boolean = false,
    ): Kept {
        val bot = publisher.ifAvailable ?: return NO_BOT
        val found = bot.findDiscordEvents(DiscordPostContent.listingLineOf(eventId, site))
        val event = liveEvent(eventId) ?: return sweep(eventId, DiscordArtefact.DISCORD_EVENT, found, GONE) { bot.deleteDiscordEvent(it) }
        val refusal =
            when {
                due(event).over -> OVER
                forced || announced(event) -> null
                else -> "The event has no #$infoChannel announcement yet, and its Discord event is made beside it."
            }
        if (refusal != null) return sweep(eventId, DiscordArtefact.DISCORD_EVENT, found, refusal) { bot.deleteDiscordEvent(it) }
        val starts = event.startTime.takeIf { it.isAfter(clock.instant()) }
        return keep(
            eventId,
            DiscordArtefact.DISCORD_EVENT,
            found,
            fingerprint = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath),
            make = { if (starts == null) null else bot.createDiscordEvent(listingOf(event, starts)) },
            update = { bot.updateDiscordEvent(it, listingOf(event, starts)) },
            delete = { bot.deleteDiscordEvent(it) },
            unmade = "The event has already started, and Discord makes no event for one in progress.",
        )
    }

    private fun announced(event: EventPostData) =
        ledger.find(event.id, DiscordArtefact.DISCORD_EVENT) != null || ledger.find(event.id, DiscordArtefact.INFO_POST) != null

    /* [refusal] answers why the post should not stand now, or null where it should. */
    private fun keepPost(
        eventId: Long,
        artefact: DiscordArtefact,
        channel: String,
        refusal: (EventPostData, DiscordPostsDue, Boolean) -> String?,
    ): Kept {
        val bot = publisher.ifAvailable ?: return NO_BOT
        val found = bot.findPosts(channel, DiscordPostContent.pageOf(eventId, site))
        val event = liveEvent(eventId) ?: return sweep(eventId, artefact, found, GONE) { bot.delete(channel, it) }
        refusal(event, due(event), ledger.find(eventId, artefact) != null)?.let { why ->
            return sweep(eventId, artefact, found, why) { bot.delete(channel, it) }
        }
        val post = DiscordPostContent.postOf(event, site)
        return keep(
            eventId,
            artefact,
            found,
            // The banner goes by its path: its bytes would read as new on every run.
            fingerprint = fingerprintOf(post to event.bannerPath),
            make = { bot.post(channel, withBanner(post, event)) },
            update = { bot.edit(channel, it, withBanner(post, event)) },
            delete = { bot.delete(channel, it) },
        )
    }

    /*
     * What should not stand goes, recorded or not: [found] is what the server holds that links the
     * event. Taking something down is the run's work; where there was nothing, it skipped [why].
     */
    private fun sweep(
        eventId: Long,
        artefact: DiscordArtefact,
        found: List<String>,
        why: String,
        delete: (String) -> Unit,
    ): Kept {
        val recorded = ledger.find(eventId, artefact)
        val standing = (found + listOfNotNull(recorded?.externalId)).distinct()
        standing.forEach(delete)
        if (recorded != null) ledger.release(eventId, artefact)
        return if (standing.isEmpty()) Kept(skipped = why) else Kept()
    }

    /*
     * What should stand is kept to one: the recorded one, or else one already in the server taken
     * over, or else a new one. [update] answers false for one removed by hand, which is made again;
     * [make] answers null where none may be made now, which skips the run [unmade].
     */
    @Suppress("LongParameterList")
    private fun keep(
        eventId: Long,
        artefact: DiscordArtefact,
        found: List<String>,
        fingerprint: Long,
        make: () -> String?,
        update: (String) -> Boolean,
        delete: (String) -> Unit,
        unmade: String? = null,
    ): Kept {
        val recorded = ledger.find(eventId, artefact)
        if (recorded != null && stillKept(eventId, artefact, recorded, found, fingerprint, update, delete)) return Kept()
        // Another run holds it, or held it and stopped; retrying finds its record or takes over its stale claim.
        check(ledger.claim(eventId, artefact, clock.instant())) { "Another run is making the $artefact of event $eventId" }
        val id =
            try {
                found.firstOrNull { update(it) } ?: make()
            } catch (refused: RuntimeException) {
                ledger.release(eventId, artefact)
                throw refused
            }
        if (id == null) {
            ledger.release(eventId, artefact)
            return Kept(skipped = unmade)
        }
        ledger.record(eventId, artefact, id, fingerprint)
        found.filter { it != id }.forEach(delete)
        return Kept(made = true)
    }

    /* The recorded one, brought up to date; false where somebody removed it by hand, its record given back. */
    @Suppress("LongParameterList")
    private fun stillKept(
        eventId: Long,
        artefact: DiscordArtefact,
        recorded: RecordedArtefact,
        found: List<String>,
        fingerprint: Long,
        update: (String) -> Boolean,
        delete: (String) -> Unit,
    ): Boolean {
        found.filter { it != recorded.externalId }.forEach(delete)
        if (recorded.fingerprint == fingerprint) return true
        if (update(recorded.externalId)) {
            ledger.record(eventId, artefact, recorded.externalId, fingerprint)
            return true
        }
        ledger.release(eventId, artefact)
        return false
    }

    private fun liveEvent(eventId: Long) = events.of(eventId)?.takeIf { it.live }

    private fun due(event: EventPostData) = DiscordPostSchedule.due(event.startTime, event.endTime, clock.instant())

    private fun withBanner(
        post: DiscordPost,
        event: EventPostData,
    ) = post.copy(
        banner = events.bannerOf(event.id)?.let { DiscordImage("banner.${it.mediaType.substringAfter('/')}", it.mediaType, it.bytes) },
    )

    private fun listingOf(
        event: EventPostData,
        starts: Instant?,
    ) = DiscordPostContent
        .listingOf(
            event,
            site,
            events.bannerOf(event.id)?.let { "data:${it.mediaType};base64,${Base64.getEncoder().encodeToString(it.bytes)}" },
        ).copy(start = starts)
}

/** What one run did: [skipped] says why it did nothing, [made] whether it put the thing up. */
data class Kept(
    val made: Boolean = false,
    val skipped: String? = null,
)

private val NO_BOT = Kept(skipped = "The Discord bot is not configured.")
private const val GONE = "The event is deleted or no longer approved."
private const val OVER = "The event is over."
private val MORNING_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm 'on' d MMMM yyyy", Locale.ENGLISH)

private fun morningOf(moment: Instant): String = MORNING_FORMAT.format(moment.atZone(DiscordPostSchedule.ZONE))

/* Eight bytes of a digest of what is said, so an edit goes out only when something changed. */
private fun fingerprintOf(content: Any): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())).long
