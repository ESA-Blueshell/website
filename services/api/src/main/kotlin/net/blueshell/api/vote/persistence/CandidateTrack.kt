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
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnDefault
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * One link picked as a game's track. The same link for the same game is the same row, so two
 * members who paste it land on one track and their points add up; the same link for another game
 * is that game's own track, so a link first added under the wrong game blocks nobody. A duplicate
 * the board merges keeps its row and points at [mergedInto], which its points count toward.
 */
@Entity
@Table(
    name = "vote_candidate_tracks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_candidate_tracks_candidate_link", columnNames = ["candidate_id", "source", "external_id"]),
    ],
)
class CandidateTrack(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    val candidate: VoteCandidate,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    val source: TrackSource,
    @Column(name = "external_id", nullable = false, length = 64)
    val externalId: String,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "added_at", nullable = false)
    val addedAt: Instant,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_file_id")
    var thumbnail: File?,
    /** The member who added the link, who may rename it as the board may; null once they are gone. */
    @Column(name = "added_by_id")
    val addedById: Long? = null,
) : AutoIdEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    var mergedInto: CandidateTrack? = null

    // Raised by every merge that reads the track, so two merges touching one track cannot both commit.
    // Declared here rather than through VersionedEntity, which would cost AutoIdEntity's id equality.
    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    var version: Long = 0L
}
