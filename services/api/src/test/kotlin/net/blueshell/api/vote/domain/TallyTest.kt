package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.CastPick
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TallyTest {
    private val points = listOf(12, 10, 8, 6, 4)

    private fun pick(
        position: Int,
        game: Long,
        track: Long? = game * 10,
    ) = CastPick(0, position, game, track)

    private fun count(vararg ballots: List<CastPick>) = Tally.count(ballots.toList(), points)

    private fun places(standings: List<Standing>) = standings.map { it.candidateId to it.place }

    @Test
    fun `a game's points are every pick of it, at what each position is worth`() {
        val standings = count(listOf(pick(1, 1), pick(2, 2)), listOf(pick(5, 1)))

        assertThat(standings.map { it.candidateId to it.points }).containsExactly(1L to 16, 2L to 10)
        assertThat(standings.first().voters).isEqualTo(2)
        assertThat(standings.first().byPosition).containsExactly(1, 0, 0, 0, 1)
    }

    @Test
    fun `a kind's own points decide what each position is worth`() {
        val standings = Tally.count(listOf(listOf(pick(1, 1), pick(2, 2))), listOf(3, 1))

        assertThat(standings.map { it.points }).containsExactly(3, 1)
        assertThat(standings.first().byPosition).hasSize(2)
    }

    @Test
    fun `a vote split over a game's tracks still counts for the game, and the best track represents it`() {
        val standings =
            count(listOf(pick(1, 1, track = 11)), listOf(pick(2, 1, track = 12)), listOf(pick(3, 1, track = 12)), listOf(pick(1, 2)))

        assertThat(standings.first().candidateId).isEqualTo(1)
        assertThat(standings.first().points).isEqualTo(30)
        assertThat(standings.first().trackId).isEqualTo(12)
    }

    @Test
    fun `a kind that picks games alone has no representative track`() {
        assertThat(count(listOf(pick(1, 1, track = null))).single().trackId).isNull()
    }

    @Test
    fun `tracks level on points go to the one more ballots named`() {
        // Track 11: one pick at 8; track 12: two picks at 4.
        val standings = count(listOf(pick(3, 1, track = 11)), listOf(pick(5, 1, track = 12)), listOf(pick(5, 1, track = 12)))

        assertThat(standings.single().trackId).isEqualTo(12)
    }

    @Test
    fun `tracks level on points and ballots go to the one added first`() {
        assertThat(count(listOf(pick(1, 1, track = 13)), listOf(pick(1, 1, track = 11))).single().trackId).isEqualTo(11)
    }

    @Test
    fun `a points tie breaks on more first places`() {
        // Game 1: 12 + 4 with a first; game 2: 10 + 6 without.
        assertThat(places(count(listOf(pick(1, 1), pick(2, 2)), listOf(pick(4, 2), pick(5, 1))))).containsExactly(1L to 1, 2L to 2)
    }

    @Test
    fun `then on more voters`() {
        // Game 1: 4 + 4 + 4 from three ballots; game 2: 8 + 4 from two. Neither has a first.
        val standings = count(listOf(pick(5, 1), pick(3, 2)), listOf(pick(5, 1), pick(4, 3)), listOf(pick(5, 1), pick(5, 2)))

        assertThat(places(standings).take(2)).containsExactly(1L to 1, 2L to 2)
    }

    @Test
    fun `then on more second places, and so on down`() {
        // Game 1: 10 + 6 with a second; game 2: 8 + 8 without. Two voters each, no first.
        val standings = count(listOf(pick(2, 1), pick(3, 2)), listOf(pick(4, 1), pick(3, 2, track = 21)))

        assertThat(places(standings)).containsExactly(1L to 1, 2L to 2)
    }

    @Test
    fun `games level on every count share a place, and the next place skips`() {
        val standings = count(listOf(pick(1, 3), pick(2, 1)), listOf(pick(1, 2), pick(2, 4)), listOf(pick(5, 5)))

        assertThat(places(standings)).containsExactly(2L to 1, 3L to 1, 1L to 3, 4L to 3, 5L to 5)
    }

    @Test
    fun `a merged track counts as the track it was merged into`() {
        val standings =
            Tally.count(
                listOf(listOf(pick(1, 1, track = 11)), listOf(pick(1, 1, track = 12))),
                points,
                canonical = { id -> if (id == 11L) 12 else id },
            )

        assertThat(standings.single().trackId).isEqualTo(12)
        assertThat(standings.single().points).isEqualTo(24)
    }

    @Test
    fun `a pick past the last position is worth nothing and is not counted`() {
        assertThat(count(listOf(pick(points.size + 1, 1)))).isEmpty()
    }
}
