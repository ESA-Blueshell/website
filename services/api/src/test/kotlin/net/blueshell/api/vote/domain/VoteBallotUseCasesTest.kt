package net.blueshell.api.vote.domain

import net.blueshell.api.vote.domain.VoteFixtures.CLOSES
import net.blueshell.api.vote.domain.VoteFixtures.NOW
import net.blueshell.api.vote.domain.VoteFixtures.OPENS
import net.blueshell.api.vote.domain.VoteFixtures.ballot
import net.blueshell.api.vote.domain.VoteFixtures.game
import net.blueshell.api.vote.domain.VoteFixtures.picking
import net.blueshell.api.vote.domain.VoteFixtures.track
import net.blueshell.api.vote.domain.VoteFixtures.vote
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.VoteBallot
import net.blueshell.api.vote.persistence.VoteBallotRepository
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Clock
import java.time.ZoneOffset

class VoteBallotUseCasesTest {
    private val votes = mock<VoteRepository>()
    private val ballots = mock<VoteBallotRepository>()
    private val games = mock<VoteCandidateRepository>()
    private val tracks = mock<CandidateTrackRepository>()
    private val service = VoteBallotUseCases(votes, ballots, games, tracks, Clock.fixed(NOW, ZoneOffset.UTC))

    private val celeste = game(1, year = 2018)
    private val hades = game(2, year = 2026)
    private val resurrections = track(11, celeste)
    private val firstSteps = track(12, celeste, mergedInto = resurrections)
    private val godOfTheDead = track(21, hades)

    private val ost = VoteKind.OST_TOP_50
    private val goty = VoteKind.GAME_OF_THE_YEAR

    init {
        val tracksById = listOf(resurrections, firstSteps, godOfTheDead).associateBy { it.id!! }
        val candidatesById = listOf(celeste, hades).associateBy { it.id!! }
        whenever(tracks.findAllByIdIn(any<Collection<Long>>())).thenAnswer { call ->
            call.getArgument<Collection<Long>>(0).mapNotNull { tracksById[it] }
        }
        whenever(games.findAllByIdIn(any<Collection<Long>>())).thenAnswer { call ->
            call.getArgument<Collection<Long>>(0).mapNotNull { candidatesById[it] }
        }
        whenever(ballots.saveAndFlush(any<VoteBallot>())).thenAnswer { it.getArgument<VoteBallot>(0) }
        whenever(votes.findByKindAndYear(ost, 2026)).thenReturn(vote())
        whenever(votes.findByKindAndYear(goty, 2026)).thenReturn(vote(kind = goty))
        whenever(votes.findLockedByKindAndYear(ost, 2026)).thenReturn(vote())
    }

    @Nested
    inner class Mine {
        @Test
        fun `is null before the member votes`() {
            assertThat(service.mine(ost, 2026, 100)).isNull()
        }

        @Test
        fun `reads the picks back in order, a merged track as the one it was merged into`() {
            whenever(ballots.findByVoteIdAndUserId(1, 100)).thenReturn(ballot(5, 100, picking(godOfTheDead), picking(firstSteps)))

            val mine = service.mine(ost, 2026, 100)!!

            assertThat(mine.picks.map { it.candidate.id to it.track?.id }).containsExactly(2L to 21L, 1L to 11L)
            assertThat(mine.version).isZero()
            assertThat(mine.submittedAt).isEqualTo(OPENS)
        }

        @Test
        fun `reads a pick without a track back without one, and refuses a vote that does not exist`() {
            whenever(ballots.findByVoteIdAndUserId(1, 100)).thenReturn(ballot(5, 100, hades to null))

            assertThat(
                service
                    .mine(ost, 2026, 100)!!
                    .picks
                    .single()
                    .track,
            ).isNull()
            assertThatThrownBy { service.mine(ost, 2030, 100) }.isInstanceOf(AnnualVoteNotFound::class.java)
        }
    }

    @Nested
    inner class Cast {
        @Test
        fun `a first ballot keeps the picks in order, as the track each counts as`() {
            val cast = service.cast(ost, 2026, 100, listOf(PickChoice(2, 21), PickChoice(1, 12)), version = null)

            assertThat(cast.picks.map { it.track?.id }).containsExactly(21L, 11L)
            assertThat(cast.submittedAt).isEqualTo(NOW)
        }

        @Test
        fun `a changed ballot replaces its picks at the version the member read`() {
            val existing = ballot(5, 100, picking(godOfTheDead)).apply { version = 3 }
            whenever(ballots.findByVoteIdAndUserId(1, 100)).thenReturn(existing)

            service.cast(ost, 2026, 100, listOf(PickChoice(1, 11)), version = 3)

            assertThat(existing.picks.map { Triple(it.position, it.candidateId, it.trackId) }).containsExactly(Triple(1, 1L, 11L))
            assertThat(existing.submittedAt).isEqualTo(NOW)
        }

        @Test
        fun `a changed ballot without the version read is refused`() {
            whenever(ballots.findByVoteIdAndUserId(1, 100)).thenReturn(ballot(5, 100, picking(godOfTheDead)).apply { version = 3 })

            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 11)), null) }
                .isInstanceOf(OptimisticLockingFailureException::class.java)
        }

        @Test
        fun `refuses a vote that is not open, before it opens and after it closes`() {
            whenever(votes.findLockedByKindAndYear(ost, 2026)).thenReturn(vote(opensAt = NOW.plusSeconds(1)))
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 11)), null) }.isInstanceOf(WrongVoteState::class.java)

            whenever(votes.findLockedByKindAndYear(ost, 2026)).thenReturn(vote(opensAt = OPENS, closesAt = NOW))
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 11)), null) }.isInstanceOf(WrongVoteState::class.java)

            whenever(votes.findLockedByKindAndYear(ost, 2026)).thenReturn(vote(closesAt = CLOSES))
            assertThat(service.cast(ost, 2026, 100, listOf(PickChoice(1, 11)), null).picks).hasSize(1)
        }

        @Test
        fun `refuses an empty ballot and one with more picks than the kind holds`() {
            assertThatThrownBy { service.cast(ost, 2026, 100, emptyList(), null) }.isInstanceOf(BallotSize::class.java)
            assertThatThrownBy { service.cast(ost, 2026, 100, List(ost.picksPerBallot + 1) { PickChoice(1, 11) }, null) }
                .isInstanceOf(BallotSize::class.java)
        }

        @Test
        fun `refuses a game or a track that does not exist`() {
            assertThatThrownBy {
                service.cast(ost, 2026, 100, listOf(PickChoice(9, 11)), null)
            }.isInstanceOf(VoteCandidateNotFound::class.java)
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 99)), null) }.isInstanceOf(TrackNotFound::class.java)
        }

        @Test
        fun `refuses a pick without a track, or with another game's, where the kind picks tracks`() {
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1)), null) }.isInstanceOf(PickNeedsTrack::class.java)
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 21)), null) }.isInstanceOf(PickNeedsTrack::class.java)
        }

        @Test
        fun `refuses one game twice, merged track or not`() {
            assertThatThrownBy { service.cast(ost, 2026, 100, listOf(PickChoice(1, 11), PickChoice(1, 12)), null) }
                .isInstanceOf(GamePickedTwice::class.java)
        }

        @Test
        fun `a kind the api does not offer takes no ballot and reads none back`() {
            assertThatThrownBy { service.cast(goty, 2026, 100, listOf(PickChoice(2)), null) }.isInstanceOf(VoteKindNotOffered::class.java)
            assertThatThrownBy { service.mine(goty, 2026, 100) }.isInstanceOf(VoteKindNotOffered::class.java)
        }

        @Test
        fun `refuses a vote that does not exist`() {
            assertThatThrownBy {
                service.cast(
                    ost,
                    2030,
                    100,
                    listOf(PickChoice(1, 11)),
                    null,
                )
            }.isInstanceOf(AnnualVoteNotFound::class.java)
        }
    }
}
