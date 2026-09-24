package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.job.DiscordPostTrigger
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
 * Brings the events-info post, the events-calendar post and the Discord event of one event to what
 * should stand at [reconcile]'s moment. Safe to run any number of times: each is created once,
 * under a claim, and edited only when its content differs from the fingerprint recorded with it.
 * Discord refusing throws, after the claim is given back, so the job running this retries.
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
    /* A claim's age is real time, whatever moment the run judges as. Settable for tests only. */
    internal var clock: Clock = Clock.systemUTC()

    fun reconcile(
        eventId: Long,
        trigger: DiscordPostTrigger,
        now: Instant,
    ) {
        val bot = publisher.ifAvailable ?: return
        val event = events.of(eventId)
        if (event == null || !event.live) {
            DiscordArtefact.entries.forEach { remove(bot, eventId, it) }
            return
        }
        val due = DiscordPostSchedule.due(event.startTime, event.endTime, now, trigger)
        val post = DiscordPostContent.postOf(event, site)
        // The events-info post stays once out; the events-calendar post only while its day lasts.
        keepPost(bot, event.id, DiscordArtefact.INFO_POST, due.infoPost, stays = true, post)
        keepPost(bot, event.id, DiscordArtefact.CALENDAR_POST, due.calendarPost, stays = false, post)
        keepDiscordEvent(bot, event, due.over)
    }

    private fun keepPost(
        bot: DiscordPublisher,
        eventId: Long,
        artefact: DiscordArtefact,
        due: Boolean,
        stays: Boolean,
        post: DiscordPost,
    ) {
        val print = fingerprintOf(post)
        val recorded = ledger.find(eventId, artefact)
        when {
            recorded == null -> if (due) create(eventId, artefact, print) { bot.post(channelOf(artefact), post) }
            !due && !stays -> remove(bot, eventId, artefact)
            recorded.fingerprint != print -> {
                bot.edit(channelOf(artefact), recorded.externalId, post)
                ledger.record(eventId, artefact, recorded.externalId, print)
            }
        }
    }

    /* Beside the events-info post, including after an attempt that failed; gone once the event is over. */
    private fun keepDiscordEvent(
        bot: DiscordPublisher,
        event: EventPostData,
        over: Boolean,
    ) {
        if (over) {
            remove(bot, event.id, DiscordArtefact.DISCORD_EVENT)
            return
        }
        val print = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath)
        val recorded = ledger.find(event.id, DiscordArtefact.DISCORD_EVENT)
        when {
            recorded == null ->
                if (ledger.find(event.id, DiscordArtefact.INFO_POST) != null) {
                    create(event.id, DiscordArtefact.DISCORD_EVENT, print) { bot.createDiscordEvent(listingOf(event)) }
                }
            recorded.fingerprint != print -> {
                bot.updateDiscordEvent(recorded.externalId, listingOf(event))
                ledger.record(event.id, DiscordArtefact.DISCORD_EVENT, recorded.externalId, print)
            }
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
    ) {
        if (!ledger.claim(eventId, artefact, clock.instant())) return
        val id =
            try {
                make()
            } catch (refused: RuntimeException) {
                ledger.release(eventId, artefact)
                throw refused
            }
        ledger.record(eventId, artefact, id, fingerprint)
    }

    private fun channelOf(artefact: DiscordArtefact) = if (artefact == DiscordArtefact.CALENDAR_POST) calendarChannel else infoChannel

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
