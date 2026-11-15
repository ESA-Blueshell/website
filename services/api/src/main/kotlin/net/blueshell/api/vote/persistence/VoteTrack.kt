package net.blueshell.api.vote.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * One link picked as a game's track. The same link is the same row, so two members who paste it
 * land on one track and their points add up. A duplicate the board merges keeps its row and points
 * at [mergedInto], which its points count toward.
 */
@Entity
@Table(
    name = "vote_tracks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_tracks_source_external_id", columnNames = ["source", "external_id"]),
    ],
)
class VoteTrack(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    val game: VoteGame,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    val source: TrackSource,
    @Column(name = "external_id", nullable = false, length = 64)
    val externalId: String,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "added_by_id")
    val addedById: Long?,
    @Column(name = "added_at", nullable = false)
    val addedAt: Instant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_file_id")
    var thumbnail: File?,
) : AutoIdEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    var mergedInto: VoteTrack? = null
}
