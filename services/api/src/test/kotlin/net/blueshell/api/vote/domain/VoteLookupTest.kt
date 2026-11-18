package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VoteLookupTest {
    private val votes = mock<VoteRepository>()
    private val opens = VoteFixtures.OPENS
    private val closes = VoteFixtures.CLOSES
    private val vote = Vote(VoteKind.OST_TOP_50, 2026, opens, closes, VoteFixtures.REVEAL)

    @Test
    fun `a vote is found by kind and year, and a missing one is refused`() {
        whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(vote)

        assertThat(votes.of(VoteKind.OST_TOP_50, 2026)).isSameAs(vote)
        assertThatThrownBy { votes.of(VoteKind.OST_TOP_50, 2025) }.isInstanceOf(AnnualVoteNotFound::class.java)
    }

    @Test
    fun `a kind the api does not offer is refused before any vote is looked up`() {
        assertThat(VoteKind.OST_TOP_50.requireOffered()).isEqualTo(VoteKind.OST_TOP_50)
        assertThatThrownBy { votes.of(VoteKind.GAME_OF_THE_YEAR, 2026) }.isInstanceOf(VoteKindNotOffered::class.java)
        verify(votes, never()).findByKindAndYear(any(), any())
    }

    @Test
    fun `only an open vote takes ballots`() {
        vote.requireVoting(opens)

        assertThatThrownBy { vote.requireVoting(opens.minusSeconds(1)) }.isInstanceOf(WrongVoteState::class.java)
        assertThatThrownBy { vote.requireVoting(closes) }.isInstanceOf(WrongVoteState::class.java)
    }

    @Test
    fun `a kind of the year's games searches that year's releases, and another any year`() {
        assertThat(VoteKind.GAME_OF_THE_YEAR.releaseYearFor(2026)).isEqualTo(2026)
        assertThat(VoteKind.OST_TOP_50.releaseYearFor(2026)).isNull()
    }

    @Test
    fun `a pick names a track where the kind asks for one, and a game of the year where it asks for that`() {
        val celeste = VoteCandidate(igdbId = 26226, name = "Celeste", firstReleaseYear = 2026, cover = null).apply { id = 1 }
        val old = VoteCandidate(igdbId = 7346, name = "Zelda", firstReleaseYear = 2017, cover = null).apply { id = 2 }
        val track = CandidateTrack(celeste, TrackSource.YOUTUBE, "x", "Resurrections", opens, null).apply { id = 11 }

        VoteKind.GAME_OF_THE_YEAR.requirePick(2026, celeste, null)
        VoteKind.OST_TOP_50.requirePick(2026, celeste, track)
        assertThatThrownBy { VoteKind.GAME_OF_THE_YEAR.requirePick(2026, old, null) }.isInstanceOf(GameNotEligible::class.java)
        assertThatThrownBy { VoteKind.GAME_OF_THE_YEAR.requirePick(2026, celeste, track) }.isInstanceOf(PickTakesNoTrack::class.java)
        assertThatThrownBy { VoteKind.OST_TOP_50.requirePick(2026, celeste, null) }.isInstanceOf(PickNeedsTrack::class.java)
        assertThatThrownBy { VoteKind.OST_TOP_50.requirePick(2026, old, track) }.isInstanceOf(PickNeedsTrack::class.java)
    }

    @Test
    fun `no ids asks for nothing`() {
        val candidates = mock<VoteCandidateRepository>()
        val tracks = mock<CandidateTrackRepository>()

        assertThat(candidates.byIds(emptyList())).isEmpty()
        assertThat(tracks.byIds(emptyList())).isEmpty()
        verify(candidates, never()).findAllByIdIn(any())
        verify(tracks, never()).findAllByIdIn(any())
    }
}
