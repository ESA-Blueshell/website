package net.blueshell.api.discord.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import java.time.Instant

/** A server emoji from any server, seen used in the association's Discord server; see the glossary. */
@Entity
@Table(name = "discord_seen_emoji")
class SeenEmoji(
    @Id
    @Column(name = "emoji_id", nullable = false, length = 20)
    override val id: String,
    @Column(name = "name", nullable = false, length = 64)
    val name: String,
    @Column(name = "animated", nullable = false)
    val animated: Boolean,
    @Column(name = "first_seen", nullable = false)
    val firstSeen: Instant,
    @Column(name = "last_seen", nullable = false)
    val lastSeen: Instant,
    @Column(name = "use_count", nullable = false)
    val useCount: Long,
) : Identifiable<String>
