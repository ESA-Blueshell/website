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
import java.util.Base64

/**
 * Brings each of the bot's three things for one event to what should stand now. Safe to run any
 * number of times: each is created once, under a claim, and edited only when its content differs
 * from the fingerprint recorded with it. Discord refusing throws, after the claim is given back,
 * so the job running this retries. Whether a late events-info post waits for the morning run is
 * the queueing side's call, not this one's.
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
    fun keepAnnouncement(eventId: Long): Boolean = keepPost(eventId, DiscordArtefact.INFO_POST) { it.infoPost }

    /** The events-calendar post, up only while the event's day lasts. */
    fun keepCalendarPost(eventId: Long) {
        keepPost(eventId, DiscordArtefact.CALENDAR_POST) { it.calendarPost }
    }

    /** The Discord event: beside the events-info post, including after an attempt that failed, and gone once the event is over. */
    fun keepDiscordEvent(eventId: Long) {
        val bot = publisher.ifAvailable ?: return
        val event = events.of(eventId)?.takeIf { it.live }
        if (event == null || DiscordPostSchedule.due(event.startTime, event.endTime, clock.instant()).over) {
            remove(bot, eventId, DiscordArtefact.DISCORD_EVENT)
            return
        }
        val print = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath)
        val recorded = ledger.find(eventId, DiscordArtefact.DISCORD_EVENT)
        when {
            recorded == null ->
                if (ledger.find(eventId, DiscordArtefact.INFO_POST) != null) {
                    create(eventId, DiscordArtefact.DISCORD_EVENT, print) { bot.createDiscordEvent(listingOf(event)) }
                }
            recorded.fingerprint != print -> {
                bot.updateDiscordEvent(recorded.externalId, listingOf(event))
                ledger.record(eventId, DiscordArtefact.DISCORD_EVENT, recorded.externalId, print)
            }
        }
    }

    private fun keepPost(
        eventId: Long,
        artefact: DiscordArtefact,
        due: (DiscordPostsDue) -> Boolean,
    ): Boolean {
        val bot = publisher.ifAvailable ?: return false
        val event = events.of(eventId)?.takeIf { it.live }
        if (event == null) {
            remove(bot, eventId, artefact)
            return false
        }
        val isDue = due(DiscordPostSchedule.due(event.startTime, event.endTime, clock.instant()))
        val post = DiscordPostContent.postOf(event, site)
        // The banner goes by its path: its bytes would read as new on every run.
        val print = fingerprintOf(post to event.bannerPath)
        val recorded = ledger.find(eventId, artefact)
        return when {
            recorded == null -> isDue && create(eventId, artefact, print) { bot.post(channelOf(artefact), withBanner(post, event)) }
            !isDue && artefact == DiscordArtefact.CALENDAR_POST -> {
                remove(bot, eventId, artefact)
                false
            }
            recorded.fingerprint != print -> {
                bot.edit(channelOf(artefact), recorded.externalId, withBanner(post, event))
                ledger.record(eventId, artefact, recorded.externalId, print)
                false
            }
            else -> false
        }
    }

    private fun remove(
        bot: DiscordPublisher,
        eventId: Long,
        artefact: DiscordArtefact,
    ) {
        val recorded = ledger.find(eventId, artefact) ?: return
        when (artefact) {
            DiscordArtefact.DISCORD_EVENT -> bot.deleteDiscordEvent(recorded.externalId)
            else -> bot.delete(channelOf(artefact), recorded.externalId)
        }
        ledger.release(eventId, artefact)
    }

    private fun create(
        eventId: Long,
        artefact: DiscordArtefact,
        fingerprint: Long,
        make: () -> String,
    ): Boolean {
        if (!ledger.claim(eventId, artefact, clock.instant())) return false
        val id =
            try {
                make()
            } catch (refused: RuntimeException) {
                ledger.release(eventId, artefact)
                throw refused
            }
        ledger.record(eventId, artefact, id, fingerprint)
        return true
    }

    private fun channelOf(artefact: DiscordArtefact) = if (artefact == DiscordArtefact.CALENDAR_POST) calendarChannel else infoChannel

    private fun withBanner(
        post: DiscordPost,
        event: EventPostData,
    ) = post.copy(
        banner = events.bannerOf(event.id)?.let { DiscordImage("banner.${it.mediaType.substringAfter('/')}", it.mediaType, it.bytes) },
    )

    private fun listingOf(event: EventPostData) =
        DiscordPostContent.listingOf(
            event,
            site,
            events.bannerOf(event.id)?.let { "data:${it.mediaType};base64,${Base64.getEncoder().encodeToString(it.bytes)}" },
        )
}

/* Eight bytes of a digest of what is said, so an edit goes out only when something changed. */
private fun fingerprintOf(content: Any): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())).long
