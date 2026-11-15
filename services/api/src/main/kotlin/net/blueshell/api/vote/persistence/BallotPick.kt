package net.blueshell.api.vote.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** One ranked pick on a ballot: a game, and its track where the kind picks one. */
@Embeddable
class BallotPick(
    @Column(name = "position", nullable = false)
    val position: Int,
    @Column(name = "game_id", nullable = false)
    val gameId: Long,
    @Column(name = "track_id")
    val trackId: Long?,
)
