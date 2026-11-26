package net.blueshell.api.vote.persistence

import net.blueshell.api.vote.domain.VoteFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class VoteRowsTest {
    @Test
    fun `Hibernate can build every vote row to load into`() {
        // The constructors the JPA compiler plugin adds, which are the only ones Hibernate calls.
        listOf(Vote::class, VoteBallot::class, VotePlacing::class, CandidateTrack::class, BallotPick::class).forEach {
            assertThat(it.java.getDeclaredConstructor().newInstance()).isNotNull
        }
    }

    @Test
    fun `a ballot, a placing and a track keep what they are stored with`() {
        val ballot = VoteBallot(voteId = 3, userId = 7, submittedAt = Instant.EPOCH)
        val placing = VotePlacing(3, 1, VoteFixtures.game(1), null, 12, 1)
        val track = VoteFixtures.track(11, VoteFixtures.game(1)).apply { version = 2 }

        assertThat(listOf(ballot.voteId, ballot.userId, placing.voteId)).containsExactly(3L, 7L, 3L)
        assertThat(track.version).isEqualTo(2)
    }
}
