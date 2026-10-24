package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmoji
import net.blueshell.api.discord.persistence.SeenEmojiRepository
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.support.TransactionOperations
import java.time.Instant

// Holds seen emoji in memory as the repository's SQL does; SeenEmojiIndexIT holds the SQL to the same.
internal class HeldSeenEmoji : SeenEmojiRepository {
    private val messages = mutableSetOf<Pair<String, String>>()
    private val held = linkedMapOf<String, SeenEmoji>()

    override fun recordMessage(
        emojiId: String,
        messageId: String,
    ): Int = if (messages.add(emojiId to messageId)) 1 else 0

    override fun countUse(
        emojiId: String,
        name: String,
        animated: Boolean,
        at: Instant,
    ): Int {
        val before = held[emojiId]
        val newest = before == null || at >= before.lastSeen
        held[emojiId] =
            SeenEmoji(
                id = emojiId,
                name = if (newest) name else before.name,
                animated = if (newest) animated else before.animated,
                firstSeen = minOf(before?.firstSeen ?: at, at),
                lastSeen = maxOf(before?.lastSeen ?: at, at),
                useCount = (before?.useCount ?: 0) + 1,
            )
        return 1
    }

    override fun findAllByOrderByUseCountDesc(): List<SeenEmoji> = held.values.sortedByDescending { it.useCount }
}

internal fun seenEmojiIndex(
    repository: SeenEmojiRepository = HeldSeenEmoji(),
    sightings: EmojiSightings? = null,
) = SeenEmojiIndex(
    repository,
    TransactionOperations.withoutTransaction(),
    mock<ObjectProvider<EmojiSightings>> { on { ifAvailable } doReturn sightings },
)
