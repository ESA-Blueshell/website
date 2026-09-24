package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.shared.discord.DiscordPost
import net.blueshell.api.shared.discord.DiscordPublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/**
 * Brings what the bot keeps in the server for one event to what should stand at [reconcile]'s
 * moment: posts what is due, edits what has changed, and removes what should no longer be there.
 * Run each morning for every event near its time and whenever an event changes, and safe to run
 * any number of times: each artefact is created once, under a claim, and edited only when its
 * content differs from the fingerprint recorded with it.
 *
 * Discord refusing throws, after giving the claim back, so the job running this retries.
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
    fun reconcile(
        eventId: Long,
        trigger: Trigger,
        now: Instant,
    ) {
        val bot = publisher.ifAvailable ?: return
        val event = events.of(eventId)
        if (event == null || !event.live) {
            removeAll(bot, eventId)
            return
        }
        val due = DiscordPostSchedule.due(event.startTime, event.endTime, event.live, now, trigger)
        val post = DiscordPostContent.postOf(event, site)
        val postPrint = fingerprintOf(post)
        val listingPrint = fingerprintOf(DiscordPostContent.listingOf(event, site, cover = null) to event.bannerPath)

        val listed = keepInfo(bot, event, due.announce, post, postPrint, listingPrint, now)
        keepDayPost(bot, event, due.dayPost, post, postPrint, now)
        if (!listed) keepListing(bot, event, due.over, listingPrint)
    }

    /* Answers whether the Discord event was just created with it, which needs nothing more this run. */
    private fun keepInfo(
        bot: DiscordPublisher,
        event: EventPostData,
        due: Boolean,
        post: DiscordPost,
        postPrint: Long,
        listingPrint: Long,
        now: Instant,
    ): Boolean {
        val out = ledger.find(event.id, DiscordArtefact.INFO)
        if (out != null) {
            if (out.fingerprint != postPrint) {
                bot.edit(infoChannel, out.externalId, post)
                ledger.record(event.id, DiscordArtefact.INFO, out.externalId, postPrint)
            }
            return false
        }
        if (!due) return false
        create(event.id, DiscordArtefact.INFO, postPrint, now) { bot.post(infoChannel, post) }
        if (ledger.find(event.id, DiscordArtefact.LISTING) == null) {
            create(event.id, DiscordArtefact.LISTING, listingPrint, now) {
                bot.list(DiscordPostContent.listingOf(event, site, coverOf(event)))
            }
        }
        return true
    }

    private fun keepDayPost(
        bot: DiscordPublisher,
        event: EventPostData,
        due: Boolean,
        post: DiscordPost,
        postPrint: Long,
        now: Instant,
    ) {
        val out = ledger.find(event.id, DiscordArtefact.DAY)
        when {
            out == null && due -> create(event.id, DiscordArtefact.DAY, postPrint, now) { bot.post(calendarChannel, post) }
            out != null && !due -> {
                bot.delete(calendarChannel, out.externalId)
                ledger.release(event.id, DiscordArtefact.DAY)
            }
            out != null && out.fingerprint != postPrint -> {
                bot.edit(calendarChannel, out.externalId, post)
                ledger.record(event.id, DiscordArtefact.DAY, out.externalId, postPrint)
            }
        }
    }

    private fun keepListing(
        bot: DiscordPublisher,
        event: EventPostData,
        over: Boolean,
        listingPrint: Long,
    ) {
        val out = ledger.find(event.id, DiscordArtefact.LISTING) ?: return
        when {
            over -> {
                bot.end(out.externalId)
                ledger.release(event.id, DiscordArtefact.LISTING)
            }
            out.fingerprint != listingPrint -> {
                bot.relist(out.externalId, DiscordPostContent.listingOf(event, site, coverOf(event)))
                ledger.record(event.id, DiscordArtefact.LISTING, out.externalId, listingPrint)
            }
        }
    }

    private fun removeAll(
        bot: DiscordPublisher,
        eventId: Long,
    ) {
        ledger.find(eventId, DiscordArtefact.INFO)?.let {
            bot.delete(infoChannel, it.externalId)
            ledger.release(eventId, DiscordArtefact.INFO)
        }
        ledger.find(eventId, DiscordArtefact.DAY)?.let {
            bot.delete(calendarChannel, it.externalId)
            ledger.release(eventId, DiscordArtefact.DAY)
        }
        ledger.find(eventId, DiscordArtefact.LISTING)?.let {
            bot.unlist(it.externalId)
            ledger.release(eventId, DiscordArtefact.LISTING)
        }
    }

    private fun create(
        eventId: Long,
        artefact: DiscordArtefact,
        fingerprint: Long,
        now: Instant,
        make: () -> String,
    ) {
        if (!ledger.claim(eventId, artefact, now)) return
        val id =
            try {
                make()
            } catch (refused: RuntimeException) {
                ledger.release(eventId, artefact)
                throw refused
            }
        ledger.record(eventId, artefact, id, fingerprint)
    }

    private fun coverOf(event: EventPostData): String? =
        events.bannerOf(event.id)?.let { "data:${it.mediaType};base64,${Base64.getEncoder().encodeToString(it.bytes)}" }
}

/* Eight bytes of a digest of what is said, so an edit goes out only when something changed. */
private fun fingerprintOf(content: Any): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(content.toString().toByteArray())).long
