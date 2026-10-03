package net.blueshell.api.discord.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import org.springframework.data.jpa.repository.JpaRepository

/** A channel moved into the archive category, and the category it is moved back to when restored. */
@Entity
@Table(name = "archived_channel")
class ArchivedChannel(
    @Id
    @Column(name = "channel_id", nullable = false, length = 32)
    val channelId: String,
    /** Null for a channel that sat outside any category. */
    @Column(name = "category_id", length = 32)
    val categoryId: String?,
) : Identifiable<String> {
    override val id: String get() = channelId
}

interface ArchivedChannelRepository : JpaRepository<ArchivedChannel, String>
