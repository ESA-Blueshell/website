package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.SitePictures
import net.blueshell.api.file.api.asImage
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.vote.domain.VoteFixtures.NOW
import net.blueshell.api.vote.domain.VoteFixtures.game
import net.blueshell.api.vote.domain.VoteFixtures.stored
import net.blueshell.api.vote.domain.VoteFixtures.track
import net.blueshell.api.vote.domain.VoteFixtures.vote
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VoteRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.ZoneOffset

class VoteCandidateUseCasesTest {
    private val catalogue = mock<GameCatalogue>()
    private val previews = mock<TrackPreviews>()
    private val pictures = mock<RemotePictures>()
    private val sitePictures = mock<SitePictures>()
    private val games = mock<VoteCandidateRepository>()
    private val tracks = mock<CandidateTrackRepository>()
    private val votes = mock<VoteRepository> { on { findByKindAndYear(VoteKind.OST_TOP_50, 2026) } doReturn vote() }

    // Counts the transactions open, so a test can tell whether a network call ran inside one.
    private var open = 0
    private val manager =
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doAnswer { SimpleTransactionStatus().also { open++ } }
            on { commit(any()) } doAnswer { open -= 1 }
            on { rollback(any()) } doAnswer { open -= 1 }
        }
    private val service =
        VoteCandidateUseCases(
            catalogue,
            previews,
            pictures,
            sitePictures,
            votes,
            games,
            tracks,
            TransactionTemplate(manager),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
    private val ost = VoteKind.OST_TOP_50

    private val celeste = game(1, "Celeste")
    private val hades = game(2, "Hades")
    private val link = "https://youtu.be/dQw4w9WgXcQ"
    private val picture = RemotePicture(byteArrayOf(1, 2, 3), "image/jpeg")

    init {
        whenever(tracks.saveAndFlush(any<CandidateTrack>())).thenAnswer { call -> call.getArgument<CandidateTrack>(0).also { it.id = 50 } }
        whenever(games.saveAndFlush(any<VoteCandidate>())).thenAnswer { call -> call.getArgument<VoteCandidate>(0).also { it.id = 9 } }
    }

    @Nested
    inner class Search {
        @Test
        fun `a query too short to mean anything is not sent`() {
            assertThat(service.search(" h ", VoteKind.OST_TOP_50, 2026)).isEmpty()
            verify(catalogue, never()).search(any(), any(), any())
        }

        @Test
        fun `a query is trimmed, and limited to the year's releases only where the kind asks`() {
            val found = CatalogueGame(26226, "Celeste", "co1", 2018)
            whenever(catalogue.search("cel", GAME_SEARCH_LIMIT, null)).thenReturn(listOf(found))

            assertThat(service.search("  cel ", VoteKind.OST_TOP_50, 2026)).containsExactly(found)
            assertThatThrownBy { service.search("cel", VoteKind.OST_TOP_50, 2030) }.isInstanceOf(AnnualVoteNotFound::class.java)
            assertThatThrownBy { service.search("cel", VoteKind.GAME_OF_THE_YEAR, 2026) }.isInstanceOf(VoteKindNotOffered::class.java)
        }

        @Test
        fun `a search result's cover is fetched from IGDB small, and refused where there is none`() {
            whenever(pictures.fetch("https://images.igdb.com/igdb/image/upload/t_cover_small/co1abc.jpg")).thenReturn(picture)

            whenever(votes.existsVotingAt(any())).thenReturn(true)

            assertThat(service.coverPreview("co1abc")).isSameAs(picture)
            assertThatThrownBy { service.coverPreview("nothere") }.isInstanceOf(CoverUnfetchable::class.java)
            assertThatThrownBy { service.coverPreview("../etc") }.isInstanceOf(CoverUnfetchable::class.java)

            whenever(votes.existsVotingAt(any())).thenReturn(false)
            assertThatThrownBy { service.coverPreview("co1abc") }.isInstanceOf(NoVoteOpen::class.java)
        }
    }

    @Nested
    inner class Games {
        @Test
        fun `a game already stored is answered as it is`() {
            whenever(games.findByIgdbId(100)).thenReturn(celeste)

            assertThat(service.storeCandidate(100, ost, 2026).name).isEqualTo("Celeste")
            verify(catalogue, never()).byId(any())
        }

        @Test
        fun `a game picked the first time is copied from the catalogue with our copy of its cover`() {
            whenever(catalogue.byId(26226)).thenReturn(CatalogueGame(26226, "Celeste", "co1abc", 2018))
            whenever(pictures.fetch("https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg")).thenReturn(picture)
            val cover = stored("vote-covers/c.webp")
            whenever(
                sitePictures.store(picture.bytes, "igdb-26226.jpg", "image/jpeg", FileType.VOTE_COVER),
            ).thenReturn(cover)

            val game = service.storeCandidate(26226, ost, 2026)

            assertThat(game).isEqualTo(CandidateSummary(9, 26226, "Celeste", 2018, cover.asImage()))
            assertThat(game.cover!!.url).isEqualTo("/files/public/vote-covers/c.webp")
        }

        @Test
        fun `a game is kept without a cover where it has none, or the copy fails`() {
            whenever(catalogue.byId(1)).thenReturn(CatalogueGame(1, "Bare", null, null))
            assertThat(service.storeCandidate(1, ost, 2026).cover).isNull()

            whenever(catalogue.byId(2)).thenReturn(CatalogueGame(2, "Unfetched", "co2", 2020))
            assertThat(service.storeCandidate(2, ost, 2026).cover).isNull()
            verify(sitePictures, never()).store(any(), any(), any(), any())
        }

        @Test
        fun `a game the catalogue does not know is refused`() {
            assertThatThrownBy { service.storeCandidate(26226, ost, 2026) }.isInstanceOf(VoteCandidateNotFound::class.java)
        }
    }

    @Nested
    inner class Tracks {
        @Test
        fun `a game nobody picked has no tracks, and a picked one lists its live tracks`() {
            assertThat(service.tracksOf(4242)).isEmpty()

            whenever(games.findByIgdbId(100)).thenReturn(celeste)
            whenever(tracks.findAllByCandidateIdAndMergedIntoIsNullOrderByAddedAt(1)).thenReturn(listOf(track(11, celeste)))
            assertThat(service.tracksOf(100).map { it.title }).containsExactly("Track 11")
        }

        @Test
        fun `a link already added for the game answers with its track, as the one it was merged into`() {
            val kept = track(11, celeste)
            whenever(games.findByIgdbId(100)).thenReturn(celeste)
            whenever(
                tracks.findByCandidateIdAndSourceAndExternalId(1, TrackSource.YOUTUBE, "dQw4w9WgXcQ"),
            ).thenReturn(track(12, celeste, mergedInto = kept))

            assertThat(service.addTrack(100, link, ost, 2026).id).isEqualTo(11)
            verify(previews, never()).of(any())
        }

        @Test
        fun `a link already added for another game becomes this game's own track`() {
            whenever(games.findByIgdbId(100)).thenReturn(celeste)
            whenever(tracks.findByCandidateIdAndSourceAndExternalId(2, TrackSource.YOUTUBE, "dQw4w9WgXcQ")).thenReturn(track(21, hades))
            whenever(previews.of(any())).thenReturn(TrackPreview("Resurrections", null))

            val added = service.addTrack(100, link, ost, 2026)

            assertThat(added.candidate.id).isEqualTo(1)
            verify(tracks).findByCandidateIdAndSourceAndExternalId(1, TrackSource.YOUTUBE, "dQw4w9WgXcQ")
        }

        @Test
        fun `a new link becomes a track, with our copy of its thumbnail`() {
            whenever(games.findByIgdbId(100)).thenReturn(celeste)
            whenever(
                previews.of(TrackLink(TrackSource.YOUTUBE, "dQw4w9WgXcQ")),
            ).thenReturn(TrackPreview("Resurrections", "https://i.ytimg.com/x.jpg"))
            whenever(pictures.fetch("https://i.ytimg.com/x.jpg")).thenReturn(picture)
            whenever(
                sitePictures.store(any(), eq("youtube-dQw4w9WgXcQ.jpg"), any(), eq(FileType.VOTE_THUMBNAIL)),
            ).thenReturn(stored("vote-thumbnails/t.webp"))

            val added = service.addTrack(100, link, ost, 2026)

            assertThat(added.title).isEqualTo("Resurrections")
            assertThat(added.thumbnail?.url).isEqualTo("/files/public/vote-thumbnails/t.webp")
            val saved = argumentCaptor<CandidateTrack>()
            verify(tracks).saveAndFlush(saved.capture())
            assertThat(saved.firstValue.addedAt).isEqualTo(NOW)
        }

        @Test
        fun `a game nobody picked yet is stored first, and a title too long is cut`() {
            whenever(catalogue.byId(26226)).thenReturn(CatalogueGame(26226, "Celeste", null, 2018))
            whenever(previews.of(any())).thenReturn(TrackPreview("x".repeat(300), null))

            val added = service.addTrack(26226, link, ost, 2026)

            assertThat(added.candidate.id).isEqualTo(9)
            assertThat(added.title).hasSize(255)
            assertThat(added.thumbnail).isNull()
        }

        @Test
        fun `a link its service does not describe is refused, and anything else before a lookup`() {
            whenever(games.findByIgdbId(100)).thenReturn(celeste)
            assertThatThrownBy { service.addTrack(100, link, ost, 2026) }.isInstanceOf(TrackLinkUnreadable::class.java)

            assertThatThrownBy { service.addTrack(100, "https://example.com", ost, 2026) }.isInstanceOf(UnsupportedTrackLink::class.java)
            verify(tracks).findByCandidateIdAndSourceAndExternalId(any(), any(), any())
        }
    }

    @Nested
    inner class Merge {
        private val kept = track(11, celeste)
        private val duplicate = track(12, celeste)
        private val earlier = track(13, celeste, mergedInto = duplicate)

        init {
            listOf(kept, duplicate, earlier, track(21, hades)).forEach { whenever(tracks.findForMerge(it.id!!)).thenReturn(it) }
        }

        @Test
        fun `counts a duplicate as the kept track, along with what was merged into the duplicate`() {
            whenever(tracks.findAllByMergedIntoId(12)).thenReturn(listOf(earlier))

            assertThat(service.merge(12, 11).id).isEqualTo(11)
            assertThat(duplicate.mergedInto).isSameAs(kept)
            assertThat(earlier.mergedInto).isSameAs(kept)
            verify(tracks).flush()
        }

        @Test
        fun `refuses merging into itself, into or from a merged track, or across games`() {
            assertThatThrownBy { service.merge(11, 11) }.isInstanceOf(TrackMergeInvalid::class.java)
            assertThatThrownBy { service.merge(11, 13) }.isInstanceOf(TrackMergeInvalid::class.java)
            assertThatThrownBy { service.merge(13, 11) }.isInstanceOf(TrackMergeInvalid::class.java)
            assertThatThrownBy { service.merge(11, 21) }.isInstanceOf(TrackMergeInvalid::class.java)
        }

        @Test
        fun `refuses a track that does not exist, on either side`() {
            whenever(tracks.findForMerge(99)).thenReturn(null)

            assertThatThrownBy { service.merge(99, 11) }.isInstanceOf(TrackNotFound::class.java)
            assertThatThrownBy { service.merge(11, 99) }.isInstanceOf(TrackNotFound::class.java)
        }
    }

    @Test
    fun `stores no game and no track unless the vote they are picked for is open`() {
        whenever(votes.findByKindAndYear(ost, 2025)).thenReturn(vote(year = 2025, closesAt = NOW.minusSeconds(60), revealAt = NOW))

        assertThatThrownBy { service.storeCandidate(100, ost, 2025) }.isInstanceOf(WrongVoteState::class.java)
        assertThatThrownBy { service.addTrack(100, link, ost, 2025) }.isInstanceOf(WrongVoteState::class.java)
        assertThatThrownBy { service.storeCandidate(100, ost, 2030) }.isInstanceOf(AnnualVoteNotFound::class.java)
        verifyNoInteractions(catalogue, previews, pictures)
    }

    @Test
    fun `asks the catalogue, the track's service and the picture hosts outside any transaction`() {
        val seen = mutableListOf<Int>()
        whenever(catalogue.byId(26226)).thenAnswer { CatalogueGame(26226, "Celeste", "co1abc", 2018).also { seen += open } }
        whenever(previews.of(any())).thenAnswer { TrackPreview("Resurrections", "https://i.ytimg.com/x.jpg").also { seen += open } }
        whenever(pictures.fetch(any())).thenAnswer { picture.also { seen += open } }

        service.addTrack(26226, link, ost, 2026)

        assertThat(seen).hasSize(4).containsOnly(0)
        verify(manager, atLeastOnce()).commit(any())
    }

    @Test
    fun `answers the game or the track somebody else stored while it was being fetched`() {
        whenever(catalogue.byId(26226)).thenReturn(CatalogueGame(26226, "Celeste", null, 2018))
        whenever(games.findByIgdbId(26226)).thenReturn(null, celeste)
        whenever(games.saveAndFlush(any<VoteCandidate>())).thenThrow(DataIntegrityViolationException("uk_vote_candidates_igdb_id"))
        assertThat(service.storeCandidate(26226, ost, 2026).name).isEqualTo("Celeste")

        whenever(games.findByIgdbId(100)).thenReturn(celeste)
        whenever(previews.of(any())).thenReturn(TrackPreview("Resurrections", null))
        val theirs = track(11, celeste)
        whenever(tracks.findByCandidateIdAndSourceAndExternalId(1, TrackSource.YOUTUBE, "dQw4w9WgXcQ")).thenReturn(null, theirs)
        whenever(tracks.saveAndFlush(any<CandidateTrack>())).thenThrow(DataIntegrityViolationException("uk_vote_candidate_tracks"))
        assertThat(service.addTrack(100, link, ost, 2026).id).isEqualTo(11)
    }

    @Test
    fun `a failed write that nobody else made is not hidden`() {
        whenever(catalogue.byId(26226)).thenReturn(CatalogueGame(26226, "Celeste", null, 2018))
        whenever(games.saveAndFlush(any<VoteCandidate>())).thenThrow(DataIntegrityViolationException("too long"))
        assertThatThrownBy { service.storeCandidate(26226, ost, 2026) }.isInstanceOf(DataIntegrityViolationException::class.java)

        whenever(games.findByIgdbId(100)).thenReturn(celeste)
        whenever(previews.of(any())).thenReturn(TrackPreview("Resurrections", null))
        whenever(tracks.saveAndFlush(any<CandidateTrack>())).thenThrow(DataIntegrityViolationException("too long"))
        assertThatThrownBy { service.addTrack(100, link, ost, 2026) }.isInstanceOf(DataIntegrityViolationException::class.java)
        verify(manager, atLeastOnce()).rollback(any())
    }
}
