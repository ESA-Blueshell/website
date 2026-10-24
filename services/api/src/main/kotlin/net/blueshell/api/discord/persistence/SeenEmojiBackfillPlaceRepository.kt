package net.blueshell.api.discord.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface SeenEmojiBackfillPlaceRepository : Repository<SeenEmojiBackfillPlace, String> {
    // A channel already there keeps its place, so a pass started again resumes rather than restarts.
    @Modifying
    @Query(value = "INSERT IGNORE INTO discord_seen_emoji_backfill (channel_id, done) VALUES (:channelId, FALSE)", nativeQuery = true)
    fun add(
        @Param("channelId") channelId: String,
    ): Int

    fun findFirstByDoneFalseOrderByIdAsc(): SeenEmojiBackfillPlace?

    @Modifying
    @Query(
        value = "UPDATE discord_seen_emoji_backfill SET before_message_id = :before, done = :done WHERE channel_id = :channelId",
        nativeQuery = true,
    )
    fun move(
        @Param("channelId") channelId: String,
        @Param("before") before: String?,
        @Param("done") done: Boolean,
    ): Int
}
