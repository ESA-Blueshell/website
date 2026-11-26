package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlaceRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * Backfills the seen emoji from the server's history, a bounded slice at a time. Each place keeps
 * where it was, so a slice that stops resumes there, and counting is per message, so a pass read
 * again, or a message also seen live, counts once.
 */
@Service
class SeenEmojiBackfill(
    private val history: ObjectProvider<DiscordHistory>,
    private val places: SeenEmojiBackfillPlaceRepository,
    private val index: SeenEmojiIndex,
    transactionManager: PlatformTransactionManager,
) {
    // Each write commits on its own: one transaction per slice would hold the counts' row locks across its Discord calls.
    // Places are looked up in their own too, since the job's transaction keeps a snapshot from before those commits.
    private val ownTransaction =
        TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }

    /** Whether a bot is set up to read the history. */
    val available: Boolean get() = history.ifAvailable != null

    /** Whether Discord sends the history's text, which holds most emoji; without it only reactions would count. */
    val readsText: Boolean get() = history.ifAvailable?.readsText() == true

    /** Why no pass can run now, or null when one can. */
    fun whyNot(): String? =
        when {
            !available -> "The Discord bot is not set up"
            !readsText -> "Message Content is off for the bot in the Discord Developer Portal, so Discord sends no message text to read"
            else -> null
        }

    /** Starts a pass: every place the bot can see now is read again from its newest message. Returns how many there are. */
    fun startPass(): Int {
        val source = history.ifAvailable ?: return 0
        val all = source.places()
        all.forEach { place -> ownTransaction.executeWithoutResult { places.restart(place) } }
        return all.size
    }

    /** Reads up to [pages] pages, place by place, and says what it read and how many places are left. */
    fun readSome(pages: Int): BackfillSlice {
        val source = history.ifAvailable ?: return BackfillSlice()
        val read = linkedSetOf<String>()
        var messages = 0L
        var sightings = 0L
        var left = pages
        while (left > 0) {
            val place = nextPlace() ?: break
            read += place.id
            var before = place.beforeMessageId
            var done = false
            while (left > 0 && !done) {
                left--
                val page = source.page(place.id, before)
                before = page?.oldestMessageId ?: before
                done = page?.last ?: true
                messages += page?.messages ?: 0
                sightings += page?.sightings?.size ?: 0
                // One emoji per transaction, as a live one is written, so a page holds no count a live sighting waits on.
                page?.sightings?.forEach { sighting -> ownTransaction.executeWithoutResult { index.saw(sighting) } }
                ownTransaction.executeWithoutResult { places.markRead(place.id, before, done) }
            }
        }
        return BackfillSlice(read.toList(), messages, sightings, ownTransaction.execute { places.countByDoneFalse() })
    }

    private fun nextPlace() = ownTransaction.execute { places.findFirstByDoneFalseOrderByIdAsc() }
}

/** What one slice of a pass read: the places, by channel id, the messages and emoji sightings in them, and the places left. */
data class BackfillSlice(
    val places: List<String> = emptyList(),
    val messages: Long = 0,
    val sightings: Long = 0,
    val left: Long = 0,
)
