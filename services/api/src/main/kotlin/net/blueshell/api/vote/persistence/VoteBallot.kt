package net.blueshell.api.vote.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.VersionedEntity
import java.time.Instant

/** A member's response to one vote: their ranked picks, kept after the reveal for them to look back on. */
@Entity
@Table(
    name = "vote_ballots",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_ballots_vote_id_user_id", columnNames = ["vote_id", "user_id"]),
    ],
)
class VoteBallot(
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant,
) : VersionedEntity(),
    Identifiable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long? = null
        internal set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vote_ballot_picks", joinColumns = [JoinColumn(name = "ballot_id")])
    @OrderBy("position")
    val picks: MutableList<BallotPick> = mutableListOf()
}
