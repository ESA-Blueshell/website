package net.blueshell.api.vote.domain

import net.blueshell.api.file.persistence.File
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.vote.persistence.BallotPick
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CastPick
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteBallot
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteKind
import java.time.Instant

/** Entities with the ids the database would have given them, for tests that never reach one. */
internal object VoteFixtures {
    val NOW: Instant = Instant.parse("2026-12-10T18:00:00Z")
    val OPENS: Instant = Instant.parse("2026-12-01T00:00:00Z")
    val CLOSES: Instant = Instant.parse("2026-12-21T22:59:00Z")
    val REVEAL: Instant = Instant.parse("2026-12-28T19:00:00Z")

    fun stored(path: String) = Entities.file(path = path)

    fun game(
        id: Long,
        name: String = "Game $id",
        year: Int? = 2000 + id.toInt(),
        cover: File? = null,
    ) = VoteCandidate(igdbId = id * 100, name = name, firstReleaseYear = year, cover = cover).also { it.id = id }

    fun track(
        id: Long,
        game: VoteCandidate,
        mergedInto: CandidateTrack? = null,
        thumbnail: File? = null,
    ) = CandidateTrack(
        candidate = game,
        source = TrackSource.YOUTUBE,
        externalId = "video${id.toString().padStart(6, '0')}",
        title = "Track $id",
        addedAt = OPENS,
        thumbnail = thumbnail,
    ).also {
        it.id = id
        it.mergedInto = mergedInto
    }

    fun vote(
        id: Long = 1,
        kind: VoteKind = VoteKind.OST_TOP_50,
        year: Int = 2026,
        opensAt: Instant = OPENS,
        closesAt: Instant = CLOSES,
        revealAt: Instant = REVEAL,
    ) = Vote(kind, year, opensAt, closesAt, revealAt).also { it.id = id }

    /** A ballot of [picks], each a game and, where given, the track picked for it. */
    fun ballot(
        id: Long,
        userId: Long,
        vararg picks: Pair<VoteCandidate, CandidateTrack?>,
        voteId: Long = 1,
    ) = VoteBallot(voteId = voteId, userId = userId, submittedAt = OPENS).also { ballot ->
        ballot.id = id
        picks.forEachIndexed { index, (game, track) -> ballot.picks += BallotPick(index + 1, game.id!!, track?.id) }
    }

    /** The picks of [cast] as the count's query answers them. */
    fun castOf(cast: List<VoteBallot>) =
        cast.flatMap { ballot -> ballot.picks.map { CastPick(ballot.id!!, it.position, it.candidateId, it.trackId) } }

    fun picking(track: CandidateTrack) = track.candidate to track
}
