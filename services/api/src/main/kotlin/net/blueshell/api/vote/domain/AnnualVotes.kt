package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteBallot
import net.blueshell.api.vote.persistence.VoteBallotRepository
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import net.blueshell.api.vote.persistence.VoteResult
import net.blueshell.api.vote.persistence.VoteResultRepository
import net.blueshell.api.vote.persistence.VoteTrack
import net.blueshell.api.vote.persistence.VoteTrackRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * A year's vote from its creation to its revealed result. It runs on the dates the board set; the
 * board may move them until the reveal, which counts the ballots once, stores the top and deletes
 * the ballots.
 */
@Service
class AnnualVotes(
    private val votes: VoteRepository,
    private val ballots: VoteBallotRepository,
    private val candidates: VoteCandidateRepository,
    private val tracks: VoteTrackRepository,
    private val results: VoteResultRepository,
    private val clock: Clock,
) {
    /** The latest year's vote of [kind], as the public page shows it. */
    @Transactional(readOnly = true)
    fun current(kind: VoteKind): VoteSummary = summaryOf(votes.findFirstByKindOrderByYearDesc(kind) ?: throw NoAnnualVote(kind))

    @Transactional
    fun create(
        kind: VoteKind,
        year: Int,
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
    ): VoteSummary {
        requireInOrder(opensAt, closesAt, revealAt)
        if (votes.findByKindAndYear(kind, year) != null) throw AnnualVoteExists(kind, year)
        return summaryOf(votes.saveAndFlush(Vote(kind, year, opensAt, closesAt, revealAt)))
    }

    /** Moves the vote's dates, until it has been revealed. */
    @Transactional
    fun reschedule(
        kind: VoteKind,
        year: Int,
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
    ): VoteSummary {
        val vote = byYear(kind, year)
        val state = vote.stateAt(clock.instant())
        if (state == VoteState.PUBLISHED) throw WrongVoteState(state)
        requireInOrder(opensAt, closesAt, revealAt)
        vote.opensAt = opensAt
        vote.closesAt = closesAt
        vote.revealAt = revealAt
        return summaryOf(votes.saveAndFlush(vote))
    }

    /** Every game's standing so far, for the board while the public sees none. */
    @Transactional(readOnly = true)
    fun standings(
        kind: VoteKind,
        year: Int,
    ): List<Placing> {
        val vote = byYear(kind, year)
        return count(vote, ballots.findAllByVoteId(vote.id!!)).map { it.placing() }
    }

    /**
     * Reveals every vote whose reveal is due and that nobody has revealed, and answers how many.
     * Each is locked while it is counted, so a second pod running this at the same time skips it.
     */
    @Transactional
    fun revealDue(): Int {
        val now = clock.instant()
        return votes.findAllByPublishedAtIsNullAndRevealAtLessThanEqual(now).count { due ->
            val vote = votes.lockById(due.id!!)?.takeIf { it.publishedAt == null } ?: return@count false
            reveal(vote, now)
            true
        }
    }

    private fun byYear(
        kind: VoteKind,
        year: Int,
    ) = votes.findByKindAndYear(kind, year) ?: throw AnnualVoteNotFound(kind, year)

    private fun reveal(
        vote: Vote,
        now: Instant,
    ) {
        val cast = ballots.findAllByVoteId(vote.id!!)
        val top = count(vote, cast).filter { it.standing.place <= vote.kind.topSize }
        results.saveAll(
            top.map { VoteResult(vote.id!!, it.standing.place, it.candidate, it.track, it.standing.points, it.standing.voters) },
        )
        vote.ballotCount = cast.size
        vote.publishedAt = now
        ballots.deleteAll(cast)
        votes.saveAndFlush(vote)
    }

    private class Counted(
        val standing: Standing,
        val candidate: VoteCandidate,
        val track: VoteTrack?,
    ) {
        fun placing() = Placing(standing.place, candidate.summary(), track?.summary(), standing.points, standing.voters)
    }

    private fun count(
        vote: Vote,
        cast: List<VoteBallot>,
    ): List<Counted> {
        val picked = tracks.findAllById(cast.flatMap { ballot -> ballot.picks.mapNotNull { it.trackId } }.toSet()).associateBy { it.id!! }
        val standings =
            Tally.count(
                cast.map { ballot -> ballot.picks.map { CountedPick(it.position, it.candidateId, it.trackId) } },
                vote.kind.points,
                canonical = { id -> picked[id]?.canonical()?.id ?: id },
            )
        val candidatesById = candidates.findAllById(standings.map { it.candidateId }.toSet()).associateBy { it.id!! }
        val tracksById = tracks.findAllById(standings.mapNotNull { it.trackId }.toSet()).associateBy { it.id!! }
        return standings.map { Counted(it, candidatesById.getValue(it.candidateId), it.trackId?.let(tracksById::getValue)) }
    }

    private fun summaryOf(vote: Vote): VoteSummary {
        val state = vote.stateAt(clock.instant())
        if (state != VoteState.PUBLISHED) return vote.summaryWith(state, ballots.countByVoteId(vote.id!!).toInt(), emptyList())
        val placings =
            results.findAllByVoteIdOrderByPlaceAscCandidateNameAsc(vote.id!!).map {
                Placing(it.place, it.candidate.summary(), it.track?.summary(), it.points, it.voters)
            }
        return vote.summaryWith(state, vote.ballotCount ?: 0, placings)
    }
}
