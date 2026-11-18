package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VotePlacing
import net.blueshell.api.vote.persistence.VotePlacingRepository
import net.blueshell.api.vote.persistence.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ShippedDevVotesTest {
    private val votes = mock<VoteRepository>()
    private val candidates = mock<VoteCandidateRepository>()
    private val tracks = mock<CandidateTrackRepository>()
    private val placings = mock<VotePlacingRepository>()
    private val manager = mock<PlatformTransactionManager> { on { getTransaction(any()) } doReturn SimpleTransactionStatus() }
    private var nextId = 1L

    private fun seeder(now: String) =
        ShippedDevVotes(votes, candidates, tracks, placings, TransactionTemplate(manager), Clock.fixed(Instant.parse(now), ZoneOffset.UTC))

    init {
        whenever(candidates.saveAndFlush(any<VoteCandidate>())).thenAnswer { call ->
            call.getArgument<VoteCandidate>(0).also { it.id = nextId++ }
        }
        whenever(tracks.saveAndFlush(any<CandidateTrack>())).thenAnswer { call ->
            call.getArgument<CandidateTrack>(0).also { it.id = nextId++ }
        }
        whenever(votes.saveAndFlush(any<Vote>())).thenAnswer { call ->
            call.getArgument<Vote>(0).also { it.id = nextId++ }
        }
    }

    @Test
    fun `writes the last three Decembers revealed with a top, and this year's open since a week ago`() {
        assertThat(seeder("2026-11-18T12:00:00Z").apply()).isEqualTo(4)

        val saved = argumentCaptor<Vote>()
        verify(votes, times(4)).saveAndFlush(saved.capture())
        assertThat(saved.allValues.map { it.year }).containsExactly(2023, 2024, 2025, 2026)
        assertThat(saved.allValues.take(3)).allMatch { it.revealedAt == it.revealAt && it.ballotCount!! > 0 }
        assertThat(saved.allValues.last().opensAt).isEqualTo(Instant.parse("2026-11-11T12:00:00Z"))
        assertThat(saved.allValues.last().revealedAt).isNull()
        val top = argumentCaptor<List<VotePlacing>>()
        verify(placings, times(3)).saveAll(top.capture())
        assertThat(top.firstValue.map { it.place }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
        val leaders = top.allValues.map { it.first().candidate.name }
        assertThat(leaders[0]).isNotEqualTo(leaders[1])
    }

    @Test
    fun `leaves every year that has a vote as it stands, and writes no vote for a year whose voting has closed`() {
        whenever(votes.findByKindAndYear(any(), any())).thenReturn(VoteFixtures.vote())
        assertThat(seeder("2026-11-18T12:00:00Z").apply()).isZero()

        whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(null)
        assertThat(seeder("2026-12-31T18:00:00Z").apply()).isZero()
        verify(votes, never()).saveAndFlush(any())
    }

    @Test
    fun `reuses the games and songs already stored, and logs a run that failed without stopping the start`() {
        val celeste = VoteCandidate(26226, "Celeste", 2018).also { it.id = 50 }
        whenever(candidates.findByIgdbId(26226)).thenReturn(celeste)
        whenever(tracks.findByCandidateIdAndSourceAndExternalId(any(), any(), any())).thenReturn(VoteFixtures.track(60, celeste))
        whenever(votes.findByKindAndYear(any(), any())).thenReturn(VoteFixtures.vote())
        seeder("2026-11-18T12:00:00Z").onReady()
        verify(candidates, never()).saveAndFlush(celeste)

        whenever(votes.findByKindAndYear(any(), any())).thenReturn(null)
        seeder("2026-11-18T12:00:00Z").onReady()
        verify(votes, times(4)).saveAndFlush(any())

        whenever(votes.findByKindAndYear(any(), any())).thenThrow(IllegalStateException("no database"))
        seeder("2026-11-18T12:00:00Z").onReady()
    }
}
