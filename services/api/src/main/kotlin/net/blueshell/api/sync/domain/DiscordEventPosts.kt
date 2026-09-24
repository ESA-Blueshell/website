package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64

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

    /** The events-info post, which stays once out; answers whether this run put it up. */
    fun keepAnnouncement(eventId: Long): Boolean =
        keepPost(eventId, DiscordArtefact.INFO_POST, infoChannel) { due, out -> out || due.infoPost }

    /** The events-calendar post, up only while the event's day lasts, and only made on its first day. */
    fun keepCalendarPost(eventId: Long) {
        keepPost(eventId, DiscordArtefact.CALENDAR_POST, calendarChannel) { due, out ->
            due.calendarPost && (out || !due.startedBeforeToday)
        }
    }

    /**
     * The Discord event: beside the events-info post, including after an attempt that failed,
     * and gone once the event is over. Made only before the event starts, which Discord insists on.
     */
    fun keepDiscordEvent(eventId: Long) {
        val bot = publisher.ifAvailable ?: return
        val found = bot.findDiscordEvents(DiscordPostContent.listingLineOf(eventId, site))
        val event = liveEvent(eventId)
        if (event == null || !discordEventStands(event)) {
            sweep(eventId, DiscordArtefact.DISCORD_EVENT, found) { bot.deleteDiscordEvent(it) }
            return
        }
        val starts = event.startTime.takeIf { it.isAfter(clock.instant()) }
        keep(
            eventId,
            DiscordArtefact.DISCORD_EVENT,
            found,
            fingerprint = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath),
            make = { if (starts == null) null else bot.createDiscordEvent(listingOf(event, starts)) },
            update = { bot.updateDiscordEvent(it, listingOf(event, starts)) },
            delete = { bot.deleteDiscordEvent(it) },
        )
    }

    private fun discordEventStands(event: EventPostData) =
        !due(event).over &&
            (ledger.find(event.id, DiscordArtefact.DISCORD_EVENT) != null || ledger.find(event.id, DiscordArtefact.INFO_POST) != null)

    private fun keepPost(
        eventId: Long,
        artefact: DiscordArtefact,
        channel: String,
        wanted: (DiscordPostsDue, Boolean) -> Boolean,
    ): Boolean {
        val bot = publisher.ifAvailable ?: return false
        val found = bot.findPosts(channel, DiscordPostContent.pageOf(eventId, site))
        val event = liveEvent(eventId)
        if (event == null || !wanted(due(event), ledger.find(eventId, artefact) != null)) {
            sweep(eventId, artefact, found) { bot.delete(channel, it) }
            return false
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

    /* What should not stand goes, recorded or not: [found] is what the server holds that links the event. */
    private fun sweep(
        eventId: Long,
        artefact: DiscordArtefact,
        found: List<String>,
        delete: (String) -> Unit,
    ) {
        val recorded = ledger.find(eventId, artefact)
        (found + listOfNotNull(recorded?.externalId)).distinct().forEach(delete)
        if (recorded != null) ledger.release(eventId, artefact)
    }

    /*
     * What should stand is kept to one: the recorded one, or else one already in the server taken
     * over, or else a new one. [update] answers false for one removed by hand, which is made again;
     * [make] answers null where none may be made now. Answers whether this run recorded it.
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
    ): Boolean {
        val recorded = ledger.find(eventId, artefact)
        if (recorded != null && stillKept(eventId, artefact, recorded, found, fingerprint, update, delete)) return false
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
        } else {
            ledger.record(eventId, artefact, id, fingerprint)
            found.filter { it != id }.forEach(delete)
        }
        return id != null
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

/* Eight bytes of a digest of what is said, so an edit goes out only when something changed. */
private fun fingerprintOf(content: Any): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())).long
