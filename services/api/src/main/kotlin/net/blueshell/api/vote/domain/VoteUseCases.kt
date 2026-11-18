package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.CastPick
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteBallotRepository
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VotePlacing
import net.blueshell.api.vote.persistence.VotePlacingRepository
import net.blueshell.api.vote.persistence.VoteRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

/**
 * A year's vote from its creation to its revealed result. It runs on the dates the board set; the
 * board may move them until the reveal, which counts the ballots once, stores the top and deletes
 * the ballots.
 */
@Service
class VoteUseCases(
    private val votes: VoteRepository,
    private val ballots: VoteBallotRepository,
    private val candidates: VoteCandidateRepository,
    private val tracks: CandidateTrackRepository,
    private val placings: VotePlacingRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    /**
     * The vote of [kind] that anybody reads: the one that opened last, so a vote the board sets
     * up early for next year does not hide this year's voting or results. Before any has opened,
     * the one that opens first.
     */
    @Transactional(readOnly = true)
    fun current(kind: VoteKind): VoteSummary {
        kind.requireOffered()
        val vote =
            votes.findFirstByKindAndOpensAtLessThanEqualOrderByOpensAtDesc(kind, clock.instant())
                ?: votes.findFirstByKindOrderByOpensAtAsc(kind)
                ?: throw NoAnnualVote(kind)
        return summaryOf(vote)
    }

    @Transactional
    fun create(
        kind: VoteKind,
        year: Int,
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
    ): VoteSummary {
        requireInOrder(opensAt, closesAt, revealAt)
        if (votes.findByKindAndYear(kind.requireOffered(), year) != null) throw AnnualVoteExists(kind, year)
        return summaryOf(votes.saveAndFlush(Vote(kind, year, opensAt, closesAt, revealAt)))
    }

    /**
     * Moves the vote's dates, until it has been revealed. [version] is the vote as the board member
     * last read it, so two of them saving the dates at once cannot overwrite each other.
     */
    @Transactional
    fun reschedule(
        kind: VoteKind,
        year: Int,
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
        version: Long,
    ): VoteSummary {
        val vote = votes.of(kind, year)
        vote.requireVersion(version)
        val state = vote.stateAt(clock.instant())
        if (state == VoteState.REVEALED) throw WrongVoteState(state)
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
        val vote = votes.of(kind, year)
        return count(vote, ballots.picksOf(vote.id!!)).map { it.placing() }
    }

    /**
     * Reveals every vote whose reveal is due and that nobody has revealed, each in a transaction of
     * its own, and answers how many. A second pod revealing the same vote fails on the vote's
     * version and stores nothing; a vote that fails otherwise is logged and left for the next run,
     * without undoing the others.
     */
    @Suppress("TooGenericExceptionCaught") // One vote's failure must not stop the reveal of the rest.
    fun revealDue(): Int {
        val now = clock.instant()
        return votes.findAllByRevealedAtIsNullAndRevealAtLessThanEqual(now).count { due ->
            try {
                transactions.execute { revealIfDue(due.id!!, now) } == true
            } catch (_: OptimisticLockingFailureException) {
                false
            } catch (e: RuntimeException) {
                log.error("[vote-reveal] could not reveal {} {}: {}", due.kind, due.year, e.message, e)
                false
            }
        }
    }

    /**
     * Opens, then closes, then the reveal after the close: a ballot checks the vote is open and
     * commits without touching the vote, so a reveal at the close could count before it lands.
     */
    private fun requireInOrder(
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
    ) {
        if (!opensAt.isBefore(closesAt) || !closesAt.isBefore(revealAt)) throw VoteDatesOutOfOrder()
    }

    // Read again inside the transaction: the vote may have been revealed since it was found.
    private fun revealIfDue(
        id: Long,
        now: Instant,
    ): Boolean {
        val vote = votes.findById(id).orElse(null)?.takeIf { it.revealedAt == null } ?: return false
        reveal(vote, now)
        return true
    }

    // Keeps every place up to the kind's top size, so candidates tied at the last place all stay.
    private fun reveal(
        vote: Vote,
        now: Instant,
    ) {
        val cast = ballots.picksOf(vote.id!!)
        val top = count(vote, cast).filter { it.standing.place <= vote.kind.topSize }
        placings.saveAll(
            top.map { VotePlacing(vote.id!!, it.standing.place, it.candidate, it.track, it.standing.points, it.standing.voters) },
        )
        vote.ballotCount = cast.distinctBy { it.ballotId }.size
        vote.revealedAt = now
        ballots.deleteAllOfVote(vote.id!!)
        votes.saveAndFlush(vote)
    }

    private class Counted(
        val standing: Standing,
        val candidate: VoteCandidate,
        val track: CandidateTrack?,
    ) {
        fun placing() = Placing(standing.place, candidate.summary(), track?.summary(), standing.points, standing.voters)
    }

    private fun count(
        vote: Vote,
        cast: List<CastPick>,
    ): List<Counted> {
        val picked = tracks.byIds(cast.mapNotNull { it.trackId })
        val standings =
            Tally.count(
                cast.groupBy { it.ballotId }.values.toList(),
                vote.kind.points,
                canonical = { id -> picked[id]?.canonical()?.id ?: id },
            )
        val candidatesById = candidates.byIds(standings.map { it.candidateId })
        val tracksById = tracks.byIds(standings.mapNotNull { it.trackId })
        return standings.map { Counted(it, candidatesById.getValue(it.candidateId), it.trackId?.let(tracksById::getValue)) }
    }

    private fun summaryOf(vote: Vote): VoteSummary {
        val state = vote.stateAt(clock.instant())
        if (state != VoteState.REVEALED) return vote.summaryWith(state, ballots.countByVoteId(vote.id!!).toInt(), emptyList())
        val placings =
            placings.findAllByVoteIdOrderByPlaceAscCandidateNameAsc(vote.id!!).map {
                Placing(it.place, it.candidate.summary(), it.track?.summary(), it.points, it.voters)
            }
        return vote.summaryWith(state, vote.ballotCount ?: 0, placings)
    }

    private companion object {
        val log: Logger = LoggerFactory.getLogger(VoteUseCases::class.java)
    }
}
