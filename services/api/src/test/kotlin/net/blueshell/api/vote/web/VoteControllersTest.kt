package net.blueshell.api.vote.web

import net.blueshell.api.file.api.Image
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.vote.domain.BallotSummary
import net.blueshell.api.vote.domain.CandidateSummary
import net.blueshell.api.vote.domain.CatalogueGame
import net.blueshell.api.vote.domain.PickChoice
import net.blueshell.api.vote.domain.PickSummary
import net.blueshell.api.vote.domain.Placing
import net.blueshell.api.vote.domain.RemotePicture
import net.blueshell.api.vote.domain.TrackSummary
import net.blueshell.api.vote.domain.VoteBallotUseCases
import net.blueshell.api.vote.domain.VoteCandidateUseCases
import net.blueshell.api.vote.domain.VoteFixtures
import net.blueshell.api.vote.domain.VoteState
import net.blueshell.api.vote.domain.VoteSummary
import net.blueshell.api.vote.domain.VoteUseCases
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.VoteKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType

class VoteControllersTest {
    private val votes = mock<VoteUseCases>()
    private val ballots = mock<VoteBallotUseCases>()
    private val candidates = mock<VoteCandidateUseCases>()
    private val member = UserPrincipal(7, "member", "h", true, setOf(Role.MEMBER), null, null)

    private val voteController = AnnualVoteController(votes)
    private val ballotController = VoteBallotController(ballots)
    private val candidateController = VoteCandidateController(candidates)

    private val ost = VoteKind.OST_TOP_50
    private val opens = VoteFixtures.OPENS
    private val closes = VoteFixtures.CLOSES
    private val reveal = VoteFixtures.REVEAL
    private val cover = Image(url = "/files/public/vote-covers/c.webp", path = "vote-covers/c.webp", width = 264, height = 352)
    private val celeste = CandidateSummary(1, 26226, "Celeste", 2018, cover)
    private val resurrections =
        TrackSummary(
            11,
            celeste,
            TrackSource.SPOTIFY,
            "x",
            "https://open.spotify.com/track/x",
            "https://open.spotify.com/embed/track/x",
            "Resurrections",
            null,
        )
    private val celesteResponse = VoteCandidateResponse(1, 26226, "Celeste", 2018, cover)
    private val resurrectionsResponse =
        CandidateTrackResponse(
            11,
            celesteResponse,
            TrackSource.SPOTIFY,
            "x",
            "https://open.spotify.com/track/x",
            "https://open.spotify.com/embed/track/x",
            "Resurrections",
            null,
        )

    @Test
    fun `a vote reads, opens on its dates, moves them and counts through its use cases`() {
        val published =
            VoteSummary(ost, 2026, 3, VoteState.REVEALED, opens, closes, reveal, 9, listOf(Placing(1, celeste, resurrections, 40, 4)))
        whenever(votes.current(ost)).thenReturn(published)
        whenever(
            votes.create(ost, 2026, opens, closes, reveal),
        ).thenReturn(published.copy(state = VoteState.UPCOMING, results = emptyList()))
        whenever(
            votes.reschedule(ost, 2026, opens, closes, reveal, 3),
        ).thenReturn(published.copy(state = VoteState.VOTING, results = emptyList()))
        whenever(votes.standings(ost, 2026)).thenReturn(listOf(Placing(1, celeste, null, 40, 4)))

        assertThat(voteController.currentAnnualVote(ost)).isEqualTo(
            AnnualVoteResponse(
                ost,
                2026,
                3,
                VoteState.REVEALED,
                opens,
                closes,
                reveal,
                9,
                listOf(VotePlacingResponse(1, celesteResponse, resurrectionsResponse, 40, 4)),
                listOf(12, 10, 8, 6, 4),
                picksTrack = true,
                releasedInYear = false,
            ),
        )
        assertThat(
            voteController.createAnnualVote(ost, CreateAnnualVoteRequest(2026, opens, closes, reveal)).state,
        ).isEqualTo(VoteState.UPCOMING)
        assertThat(
            voteController.scheduleAnnualVote(ost, 2026, ScheduleAnnualVoteRequest(opens, closes, reveal, 3)).state,
        ).isEqualTo(VoteState.VOTING)
        assertThat(voteController.annualVoteStandings(ost, 2026).single().track).isNull()
    }

    @Test
    fun `a member reads and casts their own ballot`() {
        whenever(ballots.mine(ost, 2026, 7)).thenReturn(null)
        whenever(
            ballots.cast(ost, 2026, 7, listOf(PickChoice(1, 11)), 2),
        ).thenReturn(BallotSummary(3, closes, listOf(PickSummary(celeste, resurrections))))

        assertThat(ballotController.myVoteBallot(ost, 2026, member)).isEqualTo(VoteBallotResponse(null, null, emptyList()))
        assertThat(ballotController.castVoteBallot(ost, 2026, CastVoteBallotRequest(listOf(VotePickRequest(1, 11)), 2), member))
            .isEqualTo(VoteBallotResponse(3, closes, listOf(VotePickResponse(celesteResponse, resurrectionsResponse))))
    }

    @Test
    fun `candidates, covers, tracks, adding and merging go through the candidate use cases`() {
        whenever(candidates.search("cel", ost, 2026)).thenReturn(listOf(CatalogueGame(26226, "Celeste", "co1", 2018)))
        whenever(candidates.coverPreview("co1")).thenReturn(RemotePicture(byteArrayOf(1, 2), "image/jpeg"))
        whenever(candidates.storeCandidate(26226, ost, 2026)).thenReturn(celeste)
        whenever(candidates.tracksOf(26226)).thenReturn(listOf(resurrections))
        whenever(candidates.addTrack(26226, "https://open.spotify.com/track/x", ost, 2026)).thenReturn(resurrections)
        whenever(candidates.merge(12, 11)).thenReturn(resurrections)

        assertThat(candidateController.searchVoteCandidates("cel", ost, 2026))
            .containsExactly(CatalogueGameResponse(26226, "Celeste", 2018, "co1"))
        val fetched = candidateController.voteCandidateCover("co1")
        assertThat(fetched.body).containsExactly(1, 2)
        assertThat(fetched.headers.contentType).isEqualTo(MediaType.IMAGE_JPEG)
        assertThat(fetched.headers.cacheControl).contains("max-age=604800")
        assertThat(candidateController.storeVoteCandidate(StoreVoteCandidateRequest(26226, ost, 2026))).isEqualTo(celesteResponse)
        assertThat(candidateController.voteCandidateTracks(26226)).containsExactly(resurrectionsResponse)
        assertThat(
            candidateController.addVoteTrack(AddVoteTrackRequest(26226, "https://open.spotify.com/track/x", ost, 2026)),
        ).isEqualTo(resurrectionsResponse)
        assertThat(candidateController.mergeVoteTrack(12, MergeVoteTrackRequest(11))).isEqualTo(resurrectionsResponse)
    }

    @Test
    fun `a pick's track and a ballot's version are optional`() {
        assertThat(VotePickRequest(3).choice()).isEqualTo(PickChoice(3, null))
        assertThat(VotePickRequest(3).candidateId).isEqualTo(3)
        assertThat(CastVoteBallotRequest(listOf(VotePickRequest(3))).version).isNull()
    }
}
