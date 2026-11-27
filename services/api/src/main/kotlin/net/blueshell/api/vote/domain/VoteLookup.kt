package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import java.time.Instant

/** [this], or refused where the api does not take the kind yet. */
fun VoteKind.requireOffered(): VoteKind = if (offered) this else throw VoteKindNotOffered(this)

/** The [kind] vote of [year]: every use case finds a vote this way, so an unoffered kind is never reached. */
internal fun VoteRepository.of(
    kind: VoteKind,
    year: Int,
): Vote = findByKindAndYear(kind.requireOffered(), year) ?: throw AnnualVoteNotFound(kind, year)

/** [of], with the vote read under the lock a ballot is cast under. */
internal fun VoteRepository.lockedOf(
    kind: VoteKind,
    year: Int,
): Vote = findLockedByKindAndYear(kind.requireOffered(), year) ?: throw AnnualVoteNotFound(kind, year)

/** Refused unless members may cast and change ballots at [now]. */
internal fun Vote.requireVoting(now: Instant) {
    val state = stateAt(now)
    if (state != VoteState.VOTING) throw WrongVoteState(state)
}

/** The release year a search for this kind keeps to: [year] for a kind of the year's games, else none. */
internal fun VoteKind.releaseYearFor(year: Int): Int? = year.takeIf { releasedInYear }

/** Refused where [candidate] with [track] is not a pick this kind takes in its vote of [year]. */
internal fun VoteKind.requirePick(
    year: Int,
    candidate: VoteCandidate,
    track: CandidateTrack?,
) {
    if (releasedInYear && candidate.firstReleaseYear != year) throw GameNotEligible(candidate.id!!, year)
    if (!picksTrack && track != null) throw PickTakesNoTrack(candidate.id!!)
    if (picksTrack && track?.candidate?.id != candidate.id) throw PickNeedsTrack(candidate.id!!)
}

internal fun VoteCandidateRepository.byIds(ids: Collection<Long>): Map<Long, VoteCandidate> =
    if (ids.isEmpty()) emptyMap() else findAllByIdIn(ids.toSet()).associateBy { it.id!! }

internal fun CandidateTrackRepository.byIds(ids: Collection<Long>): Map<Long, CandidateTrack> =
    if (ids.isEmpty()) emptyMap() else findAllByIdIn(ids.toSet()).associateBy { it.id!! }
