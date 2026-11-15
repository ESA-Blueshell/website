package net.blueshell.api.vote.domain

import net.blueshell.api.vote.domain.VoteFixtures.CLOSES
import net.blueshell.api.vote.domain.VoteFixtures.NOW
import net.blueshell.api.vote.domain.VoteFixtures.OPENS
import net.blueshell.api.vote.domain.VoteFixtures.REVEAL
import net.blueshell.api.vote.domain.VoteFixtures.ballot
import net.blueshell.api.vote.domain.VoteFixtures.game
import net.blueshell.api.vote.domain.VoteFixtures.picking
import net.blueshell.api.vote.domain.VoteFixtures.stored
import net.blueshell.api.vote.domain.VoteFixtures.track
import net.blueshell.api.vote.domain.VoteFixtures.vote
import net.blueshell.api.vote.persistence.AnnualVote
import net.blueshell.api.vote.persistence.AnnualVoteRepository
import net.blueshell.api.vote.persistence.VoteBallotRepository
import net.blueshell.api.vote.persistence.VoteGame
import net.blueshell.api.vote.persistence.VoteGameRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteResult
import net.blueshell.api.vote.persistence.VoteResultRepository
import net.blueshell.api.vote.persistence.VoteTrack
import net.blueshell.api.vote.persistence.VoteTrackRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.ZoneOffset

class AnnualVotesTest {
    private val votes = mock<AnnualVoteRepository>()
    private val ballots = mock<VoteBallotRepository>()
    private val games = mock<VoteGameRepository>()
    private val tracks = mock<VoteTrackRepository>()
    private val results = mock<VoteResultRepository>()
    private val service = AnnualVotes(votes, ballots, games, tracks, results, Clock.fixed(NOW, ZoneOffset.UTC))

    private val celeste = game(1, "Celeste", cover = stored("vote-covers/celeste.webp"))
    private val hades = game(2, "Hades")
    private val resurrections = track(11, celeste, thumbnail = stored("vote-thumbnails/r.webp"))
    private val firstSteps = track(12, celeste, mergedInto = resurrections)
    private val godOfTheDead = track(21, hades)

    init {
        stubFind(listOf(resurrections, firstSteps, godOfTheDead), listOf(celeste, hades))
        whenever(votes.saveAndFlush(any<AnnualVote>())).thenAnswer { call ->
            call.getArgument<AnnualVote>(0).also { if (it.id == null) it.id = 1 }
        }
    }

    private fun stubFind(
        trackRows: List<VoteTrack>,
        gameRows: List<VoteGame>,
    ) {
        val tracksById = trackRows.associateBy { it.id!! }
        val gamesById = gameRows.associateBy { it.id!! }
        whenever(tracks.findAllById(any<Iterable<Long>>())).thenAnswer { call ->
            call.getArgument<Iterable<Long>>(0).mapNotNull { tracksById[it] }
        }
        whenever(games.findAllById(any<Iterable<Long>>())).thenAnswer { call ->
            call.getArgument<Iterable<Long>>(0).mapNotNull { gamesById[it] }
        }
    }

    @Nested
    inner class Current {
        @Test
        fun `answers that there is none before a kind's first vote`() {
            assertThatThrownBy { service.current(VoteKind.OST_TOP_50) }.isInstanceOf(NoAnnualVote::class.java)
        }

        @Test
        fun `an open vote shows its state, dates and live ballot count, and no results`() {
            whenever(votes.findFirstByKindOrderByYearDesc(VoteKind.OST_TOP_50)).thenReturn(vote())
            whenever(ballots.countByVoteId(1)).thenReturn(4L)

            val summary = service.current(VoteKind.OST_TOP_50)

            assertThat(summary).isEqualTo(VoteSummary(VoteKind.OST_TOP_50, 2026, VoteState.VOTING, OPENS, CLOSES, REVEAL, 4, emptyList()))
            verify(results, never()).findAllByVoteIdOrderByPlaceAscGameNameAsc(any())
        }

        @Test
        fun `a published vote shows its stored count and results, with our own pictures`() {
            val published = vote().apply { publishedAt = REVEAL }
            whenever(votes.findFirstByKindOrderByYearDesc(VoteKind.OST_TOP_50)).thenReturn(published)
            whenever(results.findAllByVoteIdOrderByPlaceAscGameNameAsc(1)).thenReturn(
                listOf(VoteResult(1, 1, celeste, resurrections, 40, 4), VoteResult(1, 2, hades, null, 12, 1)),
            )

            val summary = service.current(VoteKind.OST_TOP_50)

            assertThat(summary.ballots).isZero()
            assertThat(summary.results.map { it.place }).containsExactly(1, 2)
            assertThat(summary.results[0].game.coverPath).isEqualTo("/files/public/vote-covers/celeste.webp")
            assertThat(summary.results[0].track!!.thumbnailPath).isEqualTo("/files/public/vote-thumbnails/r.webp")
            assertThat(summary.results[1].track).isNull()
            verify(ballots, never()).countByVoteId(any())

            published.ballotCount = 9
            assertThat(service.current(VoteKind.OST_TOP_50).ballots).isEqualTo(9)
        }
    }

    @Nested
    inner class Schedule {
        @Test
        fun `creates a vote for a kind's new year on its dates`() {
            val summary = service.create(VoteKind.GAME_OF_THE_YEAR, 2026, OPENS, CLOSES, REVEAL)

            assertThat(summary.kind).isEqualTo(VoteKind.GAME_OF_THE_YEAR)
            assertThat(summary.state).isEqualTo(VoteState.VOTING)
        }

        @Test
        fun `refuses a second vote of a kind for a year`() {
            whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(vote())

            assertThatThrownBy {
                service.create(
                    VoteKind.OST_TOP_50,
                    2026,
                    OPENS,
                    CLOSES,
                    REVEAL,
                )
            }.isInstanceOf(AnnualVoteExists::class.java)
        }

        @Test
        fun `refuses dates out of order, and a reveal before the close`() {
            assertThatThrownBy {
                service.create(
                    VoteKind.OST_TOP_50,
                    2026,
                    CLOSES,
                    OPENS,
                    REVEAL,
                )
            }.isInstanceOf(VoteDatesOutOfOrder::class.java)
            assertThatThrownBy {
                service.create(
                    VoteKind.OST_TOP_50,
                    2026,
                    OPENS,
                    OPENS,
                    REVEAL,
                )
            }.isInstanceOf(VoteDatesOutOfOrder::class.java)
            assertThatThrownBy {
                service.create(
                    VoteKind.OST_TOP_50,
                    2026,
                    OPENS,
                    CLOSES,
                    OPENS,
                )
            }.isInstanceOf(VoteDatesOutOfOrder::class.java)
        }

        @Test
        fun `moves a vote's dates, a reveal at the close included`() {
            val held = vote()
            whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(held)
            val later = CLOSES.plusSeconds(3600)

            service.reschedule(VoteKind.OST_TOP_50, 2026, OPENS, later, later)

            assertThat(held.closesAt).isEqualTo(later)
            assertThat(held.revealAt).isEqualTo(later)
        }

        @Test
        fun `refuses to move a published vote, or one that does not exist`() {
            whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(vote().apply { publishedAt = REVEAL })

            assertThatThrownBy {
                service.reschedule(
                    VoteKind.OST_TOP_50,
                    2026,
                    OPENS,
                    CLOSES,
                    REVEAL,
                )
            }.isInstanceOf(WrongVoteState::class.java)
            assertThatThrownBy { service.reschedule(VoteKind.OST_TOP_50, 2030, OPENS, CLOSES, REVEAL) }
                .isInstanceOf(AnnualVoteNotFound::class.java)
        }
    }

    @Test
    fun `standings count the ballots so far, a merged track as the one it was merged into`() {
        whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(vote())
        whenever(ballots.findAllByVoteId(1)).thenReturn(listOf(ballot(1, 100, picking(firstSteps)), ballot(2, 101, picking(godOfTheDead))))

        val standings = service.standings(VoteKind.OST_TOP_50, 2026)

        assertThat(standings.map { it.place to it.track?.id }).containsExactly(1 to 11L, 1 to 21L)
        assertThat(standings.first().track!!.url).isEqualTo("https://www.youtube.com/watch?v=video000011")
    }

    @Test
    fun `a pick of a track no longer found counts as itself`() {
        val gone = track(99, hades)
        whenever(votes.findByKindAndYear(VoteKind.OST_TOP_50, 2026)).thenReturn(vote())
        whenever(ballots.findAllByVoteId(1)).thenReturn(listOf(ballot(1, 100, picking(gone))))
        whenever(tracks.findAllById(any<Iterable<Long>>())).thenReturn(emptyList<VoteTrack>()).thenReturn(listOf(gone))

        assertThat(
            service
                .standings(VoteKind.OST_TOP_50, 2026)
                .single()
                .track
                ?.id,
        ).isEqualTo(99)
    }

    @Test
    fun `standings of a games-only vote have no tracks, and a year without a vote is refused`() {
        whenever(votes.findByKindAndYear(VoteKind.GAME_OF_THE_YEAR, 2026)).thenReturn(vote(kind = VoteKind.GAME_OF_THE_YEAR))
        whenever(ballots.findAllByVoteId(1)).thenReturn(listOf(ballot(1, 100, hades to null)))

        assertThat(service.standings(VoteKind.GAME_OF_THE_YEAR, 2026).single().track).isNull()
        assertThatThrownBy { service.standings(VoteKind.OST_TOP_50, 2030) }.isInstanceOf(AnnualVoteNotFound::class.java)
    }

    @Nested
    inner class Reveal {
        @Test
        fun `reveals each due vote, storing the counted top, keeping the count and deleting the ballots`() {
            val due = vote(revealAt = NOW)
            val cast = listOf(ballot(1, 100, picking(firstSteps), picking(godOfTheDead)), ballot(2, 101, picking(godOfTheDead)))
            whenever(votes.findAllByPublishedAtIsNullAndRevealAtLessThanEqual(NOW)).thenReturn(listOf(due))
            whenever(votes.lockById(1)).thenReturn(due)
            whenever(ballots.findAllByVoteId(1)).thenReturn(cast)
            val saved = argumentCaptor<List<VoteResult>>()

            assertThat(service.revealDue()).isEqualTo(1)

            verify(results).saveAll(saved.capture())
            assertThat(
                saved.firstValue.map { Triple(it.place, it.track?.id, it.points) },
            ).containsExactly(Triple(1, 21L, 22), Triple(2, 11L, 12))
            assertThat(saved.firstValue.map { it.game.name to it.voters }).containsExactly("Hades" to 2, "Celeste" to 1)
            assertThat(due.ballotCount).isEqualTo(2)
            assertThat(due.publishedAt).isEqualTo(NOW)
            verify(ballots).deleteAll(cast)
        }

        @Test
        fun `skips a vote another pod revealed or removed since it was found`() {
            val due = vote(revealAt = NOW)
            whenever(votes.findAllByPublishedAtIsNullAndRevealAtLessThanEqual(NOW)).thenReturn(listOf(due, vote(id = 2, revealAt = NOW)))
            whenever(votes.lockById(1)).thenReturn(vote(revealAt = NOW).apply { publishedAt = NOW })
            whenever(votes.lockById(2)).thenReturn(null)

            assertThat(service.revealDue()).isZero()
            verify(results, never()).saveAll(any<List<VoteResult>>())
        }

        @Test
        fun `keeps only the kind's top places`() {
            // Game k is named first on k ballots, so every game has its own place and the one with a
            // single ballot is the first left out of a top of ten.
            val many = (1L..11L).map { k -> game(100 + k, year = 2026) }
            stubFind(emptyList(), many)
            var ballotId = 0L
            val cast = many.flatMapIndexed { index, game -> List(index + 1) { ballotId++.let { ballot(it, it, game to null) } } }
            val due = vote(kind = VoteKind.GAME_OF_THE_YEAR, revealAt = NOW)
            whenever(votes.findAllByPublishedAtIsNullAndRevealAtLessThanEqual(NOW)).thenReturn(listOf(due))
            whenever(votes.lockById(1)).thenReturn(due)
            whenever(ballots.findAllByVoteId(1)).thenReturn(cast)
            val saved = argumentCaptor<List<VoteResult>>()

            service.revealDue()

            verify(results).saveAll(saved.capture())
            assertThat(saved.firstValue).hasSize(VoteKind.GAME_OF_THE_YEAR.topSize)
            assertThat(saved.firstValue.map { it.game.id }).doesNotContain(101L)
        }
    }
}
