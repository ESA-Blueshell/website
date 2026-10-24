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
    // Each page commits on its own: one transaction per slice would hold the counts' row locks across its Discord calls.
    private val eachPage =
        TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }

    /** Whether a bot is set up to read the history. */
    val available: Boolean get() = history.ifAvailable != null

    /** Starts a pass: every place the bot can see now is read again from its newest message. */
    fun startPass() {
        val source = history.ifAvailable ?: return
        source.places().forEach { place -> eachPage.executeWithoutResult { places.restart(place) } }
    }

    /** Reads up to [pages] pages, place by place; true while a place has more to read. */
    fun readSome(pages: Int): Boolean {
        val source = history.ifAvailable ?: return false
        var left = pages
        while (left > 0) {
            val place = places.findFirstByDoneFalseOrderByIdAsc() ?: return false
            var before = place.beforeMessageId
            var done = false
            while (left > 0 && !done) {
                left--
                val page = source.page(place.id, before)
                before = page?.oldestMessageId ?: before
                done = page?.last ?: true
                eachPage.executeWithoutResult {
                    page?.sightings?.forEach(index::saw)
                    places.markRead(place.id, before, done)
                }
            }
        }
        return places.findFirstByDoneFalseOrderByIdAsc() != null
    }
}
