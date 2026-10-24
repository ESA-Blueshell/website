package net.blueshell.api.discord.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable

/** Where the seen emoji backfill is in one channel, thread or forum post. */
@Entity
@Table(name = "discord_seen_emoji_backfill")
class SeenEmojiBackfillPlace(
    @Id
    @Column(name = "channel_id", nullable = false, length = 20)
    override val id: String,
    @Column(name = "before_message_id", length = 20)
    val beforeMessageId: String?,
    @Column(name = "done", nullable = false)
    val done: Boolean,
) : Identifiable<String>
