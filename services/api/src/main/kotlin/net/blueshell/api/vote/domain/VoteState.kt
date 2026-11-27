package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.Vote
import java.time.Instant

/** Where a vote stands, read off its dates and whether it has been counted. */
enum class VoteState {
    /** Before it opens. */
    UPCOMING,

    /** Members may cast and change their ballot. */
    VOTING,

    /** Closed, and not yet counted: the board merges duplicate tracks until the reveal. */
    CLOSED,

    /** Counted: the results are stored and public, and the ballots are gone. */
    REVEALED,
}

fun Vote.stateAt(now: Instant): VoteState =
    when {
        revealedAt != null -> VoteState.REVEALED
        now.isBefore(opensAt) -> VoteState.UPCOMING
        now.isBefore(closesAt) -> VoteState.VOTING
        else -> VoteState.CLOSED
    }
