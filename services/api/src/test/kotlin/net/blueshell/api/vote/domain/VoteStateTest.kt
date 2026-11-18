package net.blueshell.api.vote.domain

import net.blueshell.api.vote.domain.VoteFixtures.CLOSES
import net.blueshell.api.vote.domain.VoteFixtures.OPENS
import net.blueshell.api.vote.domain.VoteFixtures.vote
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VoteStateTest {
    @Test
    fun `a vote follows its dates, and is published once counted whatever the clock says`() {
        val vote = vote()

        assertThat(vote.stateAt(OPENS.minusSeconds(1))).isEqualTo(VoteState.UPCOMING)
        assertThat(vote.stateAt(OPENS)).isEqualTo(VoteState.VOTING)
        assertThat(vote.stateAt(CLOSES.minusSeconds(1))).isEqualTo(VoteState.VOTING)
        assertThat(vote.stateAt(CLOSES)).isEqualTo(VoteState.CLOSED)

        vote.revealedAt = CLOSES
        assertThat(vote.stateAt(OPENS)).isEqualTo(VoteState.REVEALED)
    }
}
