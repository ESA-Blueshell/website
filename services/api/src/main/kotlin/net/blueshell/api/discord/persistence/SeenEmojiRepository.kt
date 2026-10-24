package net.blueshell.api.discord.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SeenEmojiRepository : Repository<SeenEmoji, String> {
    // One row per emoji and message is the key, so only the first sighting of a message lands.
    @Modifying
    @Query(
        value = "INSERT IGNORE INTO discord_seen_emoji_message (emoji_id, message_id) VALUES (:emojiId, :messageId)",
        nativeQuery = true,
    )
    fun recordMessage(
        @Param("emojiId") emojiId: String,
        @Param("messageId") messageId: String,
    ): Int

    // The name and animation follow the newest sighting, so a backfill reading old messages keeps a rename.
    @Modifying
    @Query(
        value =
            "INSERT INTO discord_seen_emoji (emoji_id, name, animated, first_seen, last_seen, use_count) " +
                "VALUES (:emojiId, :name, :animated, :at, :at, 1) ON DUPLICATE KEY UPDATE " +
                "name = IF(:at >= last_seen, :name, name), animated = IF(:at >= last_seen, :animated, animated), " +
                "first_seen = LEAST(first_seen, :at), last_seen = GREATEST(last_seen, :at), use_count = use_count + 1",
        nativeQuery = true,
    )
    fun countUse(
        @Param("emojiId") emojiId: String,
        @Param("name") name: String,
        @Param("animated") animated: Boolean,
        @Param("at") at: Instant,
    ): Int

    fun findAllByOrderByUseCountDesc(): List<SeenEmoji>
}
