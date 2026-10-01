package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.sync.api.DiscordImage
import net.blueshell.api.sync.api.DiscordPost
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.api.sync.domain.DiscordPostSchedule.calendarPostFrom
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
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
    // Settable for tests only.
    internal var clock: Clock = Clock.systemUTC()

    /** The events-info post, which stays once out. A forced run posts it before its announce at. */
    fun keepAnnouncement(
        eventId: Long,
        forced: Boolean = false,
    ): Kept =
        keepPost(eventId, DiscordArtefact.INFO_POST, infoChannel, { null }) { event, due, out ->
            when {
                out -> null
                due.over -> OVER
                forced || due.announce -> null
                else -> "The #$infoChannel announcement is not due until ${morningOf(event.announceAt ?: event.startTime)}."
            }
        }

    /**
     * The events-calendar post, due from 08:00 on the event's first day and down for good at 08:00
     * the morning after its last. Once out, a forced one included, it stays until then.
     */
    fun keepCalendarPost(
        eventId: Long,
        forced: Boolean = false,
    ): Kept {
        val dayOver = "The event's day is over, so its #$calendarChannel post has come down."
        return keepPost(eventId, DiscordArtefact.CALENDAR_POST, calendarChannel, { due -> dayOver.takeIf { due.calendarPostOver } }) {
            event,
            due,
            out,
            ->
            when {
                due.calendarPostOver -> dayOver
                due.calendarPost || out || forced -> null
                else -> "The #$calendarChannel post is not due until ${morningOf(calendarPostFrom(event.startTime))}."
            }
        }
    }

    /**
     * The Discord event, listed once the event is approved however far ahead, and left alone once
     * it is over: Discord ends an external event at its end by itself, and an event moved ahead
     * again after that gets a new one. Discord refuses a start in the past, so one made for an event
     * already running starts a minute from now, and an edit then leaves the start as Discord has it.
     */
    fun keepDiscordEvent(eventId: Long): Kept {
        val bot = publisher.ifAvailable ?: return NO_BOT
        val stored = events.of(eventId)
        if (stored != null && due(stored).over) return Kept(skipped = ENDED)
        if (stored?.frozen == true) return Kept(skipped = FROZEN)
        val found = bot.findDiscordEvents(DiscordPostContent.listingLineOf(eventId, site))
        val unlist: (String) -> Unit = { bot.deleteDiscordEvent(it) }
        val event = stored?.takeIf { it.live } ?: return sweep(eventId, DiscordArtefact.DISCORD_EVENT, found, GONE, unlist)
        val now = clock.instant()
        val madeStarting = maxOf(event.startTime, now.plus(DISCORD_EVENT_LEAD))
        return keep(
            eventId,
            DiscordArtefact.DISCORD_EVENT,
            found,
            fingerprint = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath),
            Remote(
                make = { if (event.endTime.isAfter(madeStarting)) bot.createDiscordEvent(listingOf(event, madeStarting)) else null },
                update = { bot.updateDiscordEvent(it, listingOf(event, event.startTime.takeIf { start -> start.isAfter(now) })) },
                delete = unlist,
                locate = { it.takeIf(bot::stillListed) },
                link = { bot.linkOfDiscordEvent(it) },
            ),
            unmade = "The event ends within a minute, too soon for Discord to list it.",
        )
    }

    /*
     * [refusal] answers why the post should not stand now, or null where it should; [downWhileFrozen]
     * why it comes down by time while the event awaits re-approval, or null where it stays.
     */
    private fun keepPost(
        eventId: Long,
        artefact: DiscordArtefact,
        channel: String,
        downWhileFrozen: (DiscordPostsDue) -> String?,
        refusal: (EventPostData, DiscordPostsDue, Boolean) -> String?,
    ): Kept {
        val bot = publisher.ifAvailable ?: return NO_BOT
        val found = bot.findPosts(channel, DiscordPostContent.pageOf(eventId, site))
        val stored = events.of(eventId)
        if (stored?.frozen == true) return frozen(eventId, artefact, found, downWhileFrozen(due(stored))) { bot.delete(channel, it) }
        val event = stored?.takeIf { it.live } ?: return sweep(eventId, artefact, found, GONE) { bot.delete(channel, it) }
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
            Remote(
                make = { bot.post(channel, withBanner(post, event)) },
                update = { bot.edit(channel, it, withBanner(post, event)) },
                delete = { bot.delete(channel, it) },
                locate = { bot.stillPosted(channel, it) },
                link = { bot.linkOf(channel, it) },
            ),
        )
    }

    // What is out while the event awaits re-approval stays as last approved; only what comes down by time goes, [down] saying why.
    private fun frozen(
        eventId: Long,
        artefact: DiscordArtefact,
        found: List<String>,
        down: String?,
        delete: (String) -> Unit,
    ): Kept = if (down == null) Kept(skipped = FROZEN) else sweep(eventId, artefact, found, down, delete)

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
        return if (standing.isEmpty()) Kept(skipped = why) else Kept(JobEffect.REMOVED)
    }

    /*
     * What should stand is kept to one: the recorded one, or else one already in the server taken
     * over, or else a new one, which notifies as a first post does; one removed by hand is made
     * again. [Remote.make] answers null where none may be made now, which skips the run [unmade].
     */
    @Suppress("LongParameterList")
    private fun keep(
        eventId: Long,
        artefact: DiscordArtefact,
        found: List<String>,
        fingerprint: Long,
        remote: Remote,
        unmade: String? = null,
    ): Kept {
        ledger.find(eventId, artefact)?.let { recorded ->
            stillKept(eventId, artefact, recorded, found, fingerprint, remote)?.let { return it }
        }
        // Another run holds it, or held it and stopped; retrying finds its record or takes over its stale claim.
        check(ledger.claim(eventId, artefact, clock.instant())) { "Another run is making the $artefact of event $eventId" }
        var takenOver = false
        val id =
            try {
                found.firstOrNull { remote.update(it) }?.also { takenOver = true } ?: remote.make()
            } catch (refused: RuntimeException) {
                ledger.release(eventId, artefact)
                throw refused
            }
        if (id == null) {
            ledger.release(eventId, artefact)
            return Kept(skipped = unmade)
        }
        ledger.record(eventId, artefact, id, fingerprint)
        found.filter { it != id }.forEach(remote.delete)
        return Kept(if (takenOver) JobEffect.EDITED else JobEffect.MADE, remote.link(id))
    }

    /*
     * The recorded one, asked after by its reference on every run and brought up to date; null
     * where somebody removed it by hand, its record given back. A record naming it the old way is
     * rewritten to the reference Discord answers now.
     */
    @Suppress("LongParameterList")
    private fun stillKept(
        eventId: Long,
        artefact: DiscordArtefact,
        recorded: RecordedArtefact,
        found: List<String>,
        fingerprint: Long,
        remote: Remote,
    ): Kept? {
        val standing = remote.locate(recorded.externalId)
        val effect =
            when {
                standing == null -> null
                recorded.fingerprint == fingerprint -> JobEffect.UNCHANGED
                remote.update(standing) -> JobEffect.EDITED
                else -> null
            }
        if (standing == null || effect == null) {
            ledger.release(eventId, artefact)
            return null
        }
        found.filter { it != standing }.forEach(remote.delete)
        if (effect == JobEffect.EDITED || standing != recorded.externalId) ledger.record(eventId, artefact, standing, fingerprint)
        return Kept(effect, remote.link(standing))
    }

    private fun due(event: EventPostData) = DiscordPostSchedule.due(event.startTime, event.endTime, event.announceAt, clock.instant())

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

/**
 * How one of the three is reached in the server, by the reference its record holds. [locate]
 * answers where the one a reference names stands now, as it should be recorded, or null where it
 * is gone; [update] answers false for one gone; [link] is where it opens in Discord.
 */
private class Remote(
    val make: () -> String?,
    val update: (String) -> Boolean,
    val delete: (String) -> Unit,
    val locate: (String) -> String?,
    val link: (String) -> String?,
)

/** What one run did: its [effect] on the thing and [link] to it, or else why it [skipped] doing anything. */
data class Kept(
    val effect: JobEffect? = null,
    val link: String? = null,
    val skipped: String? = null,
)

private val NO_BOT = Kept(skipped = "The Discord bot is not configured.")
private const val GONE = "The event is deleted or no longer approved."
private const val OVER = "The event is over."
private const val FROZEN = "The event awaits re-approval, so what is out stays as last approved."
private const val ENDED = "The event is over, and Discord ends its Discord event by itself."

// Room for the request to reach Discord before the start it names.
private val DISCORD_EVENT_LEAD: Duration = Duration.ofMinutes(1)
private val MORNING_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm 'on' d MMMM yyyy", Locale.ENGLISH)

private fun morningOf(moment: Instant): String = MORNING_FORMAT.format(moment.atZone(DiscordPostSchedule.ZONE))

// Eight bytes of a digest of what is said, so an edit goes out only when something changed.
private fun fingerprintOf(content: Any): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())).long
