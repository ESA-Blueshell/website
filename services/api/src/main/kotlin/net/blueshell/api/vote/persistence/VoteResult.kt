package net.blueshell.api.vote.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AutoIdEntity

/** A game's place in a revealed vote, frozen so a later recount cannot move it. */
@Entity
@Table(
    name = "vote_results",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vote_results_vote_id_game_id", columnNames = ["vote_id", "game_id"]),
    ],
)
class VoteResult(
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "place", nullable = false)
    val place: Int,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    val game: VoteGame,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    val track: VoteTrack?,
    @Column(name = "points", nullable = false)
    val points: Int,
    @Column(name = "voters", nullable = false)
    val voters: Int,
) : AutoIdEntity()
