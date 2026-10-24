package net.blueshell.api.discord.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

// HeldPlaces in BackfillSeenEmojiJobTest mirrors these statements.
interface SeenEmojiBackfillPlaceRepository : Repository<SeenEmojiBackfillPlace, String> {
    @Modifying
    @Query(
        value =
            "INSERT INTO discord_seen_emoji_backfill (channel_id, before_message_id, done) VALUES (:channelId, NULL, FALSE) " +
                "ON DUPLICATE KEY UPDATE before_message_id = NULL, done = FALSE",
        nativeQuery = true,
    )
    fun restart(
        @Param("channelId") channelId: String,
    )

    fun findFirstByDoneFalseOrderByIdAsc(): SeenEmojiBackfillPlace?

    // Cleared after, so the next lookup reads the place as it now is rather than as first loaded.
    @Modifying(clearAutomatically = true)
    @Query(
        value = "UPDATE discord_seen_emoji_backfill SET before_message_id = :before, done = :done WHERE channel_id = :channelId",
        nativeQuery = true,
    )
    fun markRead(
        @Param("channelId") channelId: String,
        @Param("before") before: String?,
        @Param("done") done: Boolean,
    )
}
