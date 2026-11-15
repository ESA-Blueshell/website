package net.blueshell.api.vote.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class VoteEntitiesTest {
    @Test
    fun `Hibernate can build every vote entity to load a row into`() {
        // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
        val loaded =
            listOf(
                AnnualVote::class.java,
                VoteGame::class.java,
                VoteTrack::class.java,
                VoteBallot::class.java,
                BallotPick::class.java,
                VoteResult::class.java,
            ).map { it.getDeclaredConstructor().newInstance() }

        assertThat(loaded).hasSize(6)
    }

    @Test
    fun `a ballot names its vote and voter, and its id is the database's`() {
        val ballot = VoteBallot(voteId = 3, userId = 7, submittedAt = Instant.EPOCH)

        assertThat(ballot.id).isNull()
        assertThat(ballot.voteId to ballot.userId).isEqualTo(3L to 7L)
    }

    @Test
    fun `a result names its vote`() {
        val game = VoteGame(igdbId = 1, name = "Celeste")

        assertThat(VoteResult(voteId = 3, place = 1, game = game, track = null, points = 12, voters = 1).voteId).isEqualTo(3)
    }

    @Test
    fun `each kind holds as many picks as it has points, and says what it picks`() {
        assertThat(VoteKind.OST_TOP_50.picksPerBallot).isEqualTo(5)
        assertThat(VoteKind.OST_TOP_50.picksTrack).isTrue
        assertThat(VoteKind.GAME_OF_THE_YEAR.picksTrack).isFalse
        assertThat(VoteKind.GAME_OF_THE_YEAR.releasedInYear).isTrue
    }
}
