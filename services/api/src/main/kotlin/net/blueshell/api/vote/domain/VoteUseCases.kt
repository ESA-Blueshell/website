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
 * board may move them until the reveal, which counts the ballots once and stores the top. The
 * ballots stay, each one its member's to look back on.
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
        picks: Int = kind.defaultPicks,
    ): VoteSummary {
        requireInOrder(opensAt, closesAt, revealAt)
        requirePicks(picks)
        if (votes.findByKindAndYear(kind.requireOffered(), year) != null) throw AnnualVoteExists(kind, year)
        return summaryOf(votes.saveAndFlush(Vote(kind, year, opensAt, closesAt, revealAt, picks)))
    }

    /**
     * Moves the vote's dates, until it has been revealed, and sets its number of [picks] until the
     * first ballot is cast. [version] is the vote as the board member last read it, so two of them
     * saving at once cannot overwrite each other.
     */
    @Suppress("LongParameterList") // The three dates are the board's form as it is sent.
    @Transactional
    fun reschedule(
        kind: VoteKind,
        year: Int,
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
        version: Long,
        picks: Int? = null,
    ): VoteSummary {
        val vote = votes.of(kind, year)
        vote.requireVersion(version)
        val state = vote.stateAt(clock.instant())
        if (state == VoteState.REVEALED) throw WrongVoteState(state)
        requireInOrder(opensAt, closesAt, revealAt)
        if (picks != null && picks != vote.picks) {
            requirePicks(picks)
            if (ballots.countByVoteId(vote.id!!) > 0) throw VotePicksFixed()
            vote.picks = picks
        }
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
     * its own, and answers how many. A second pod revealing the same vote waits on the first's lock
     * and then finds it revealed; a vote that fails otherwise is logged and left for the next run,
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

    /** Opens, then closes, then the reveal after the close. */
    private fun requireInOrder(
        opensAt: Instant,
        closesAt: Instant,
        revealAt: Instant,
    ) {
        if (!opensAt.isBefore(closesAt) || !closesAt.isBefore(revealAt)) throw VoteDatesOutOfOrder()
    }

    private fun requirePicks(picks: Int) {
        if (picks !in MIN_PICKS..MAX_PICKS) throw VotePicksOutOfRange(MIN_PICKS, MAX_PICKS)
    }

    // Read again, locked, inside the transaction: the vote may have been revealed since it was found.
    private fun revealIfDue(
        id: Long,
        now: Instant,
    ): Boolean {
        val vote = votes.findForReveal(id)?.takeIf { it.revealedAt == null } ?: return false
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
                pointsFor(vote.picks),
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
