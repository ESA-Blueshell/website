package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.BallotPick
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteBallot
import net.blueshell.api.vote.persistence.VoteBallotRepository
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** A member's own ballot: reading it back, and casting or changing it while the vote is open. */
@Service
class VoteBallotUseCases(
    private val votes: VoteRepository,
    private val ballots: VoteBallotRepository,
    private val candidates: VoteCandidateRepository,
    private val tracks: CandidateTrackRepository,
    private val clock: Clock,
) {
    /** The member's ballot for the vote, or null when they have not voted. */
    @Transactional(readOnly = true)
    fun mine(
        kind: VoteKind,
        year: Int,
        userId: Long,
    ): BallotSummary? {
        val vote = votes.of(kind, year)
        return ballots.findByVoteIdAndUserId(vote.id!!, userId)?.let(::summaryOf)
    }

    /**
     * Replaces the member's picks with [choices], best first. [version] is the ballot as the member
     * last read it, and is required once they have one, so two tabs cannot overwrite each other.
     */
    @Transactional
    fun cast(
        kind: VoteKind,
        year: Int,
        userId: Long,
        choices: List<PickChoice>,
        version: Long?,
    ): BallotSummary {
        val vote = votes.lockedOf(kind, year)
        val now = clock.instant()
        vote.requireVoting(now)
        // TWIN: `withPick` in the frontend's `ballot.ts` keeps a ballot to these two rules while it is edited.
        if (choices.isEmpty() || choices.size > vote.picks) throw BallotSize(choices.size, vote.picks)

        val picks = resolve(vote, choices)
        picks
            .groupBy { it.first.id!! }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { throw GamePickedTwice(it.key) }

        val ballot =
            ballots.findByVoteIdAndUserId(vote.id!!, userId)?.apply {
                requireVersion(version ?: -1)
                submittedAt = now
            } ?: VoteBallot(voteId = vote.id!!, userId = userId, submittedAt = now)
        ballot.picks.clear()
        picks.forEachIndexed { index, (candidate, track) -> ballot.picks += BallotPick(index + 1, candidate.id!!, track?.id) }
        return summaryOf(ballots.saveAndFlush(ballot))
    }

    // Each choice as the game and the track it counts as, checked against what the kind asks for.
    private fun resolve(
        vote: Vote,
        choices: List<PickChoice>,
    ): List<Pair<VoteCandidate, CandidateTrack?>> {
        val candidatesById = candidates.byIds(choices.map { it.candidateId })
        val tracksById = tracks.byIds(choices.mapNotNull { it.trackId })
        return choices.map { choice ->
            val candidate = candidatesById[choice.candidateId] ?: throw VoteCandidateNotFound(choice.candidateId)
            val track = choice.trackId?.let { id -> (tracksById[id] ?: throw TrackNotFound(id)).canonical() }
            vote.kind.requirePick(vote.year, candidate, track)
            candidate to track
        }
    }

    private fun summaryOf(ballot: VoteBallot): BallotSummary {
        val candidatesById = candidates.byIds(ballot.picks.map { it.candidateId })
        val tracksById = tracks.byIds(ballot.picks.mapNotNull { it.trackId })
        return BallotSummary(
            version = ballot.version,
            submittedAt = ballot.submittedAt,
            picks =
                ballot.picks.map { pick ->
                    PickSummary(
                        candidatesById.getValue(pick.candidateId).summary(),
                        pick.trackId?.let { tracksById.getValue(it).canonical().summary() },
                    )
                },
        )
    }
}
