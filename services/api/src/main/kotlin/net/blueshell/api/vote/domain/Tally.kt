package net.blueshell.api.vote.domain

/** One pick as the count reads it. [position] is 1-based; [trackId] is the track as picked, if any. */
data class CountedPick(
    val position: Int,
    val candidateId: Long,
    val trackId: Long?,
)

/**
 * A candidate's standing: its [points], how many ballots named it ([voters]), how many named it at each
 * position ([byPosition], index 0 for first), and the track that earned it the most points, if
 * the kind picks tracks.
 */
data class Standing(
    val place: Int,
    val candidateId: Long,
    val trackId: Long?,
    val points: Int,
    val voters: Int,
    val byPosition: List<Int>,
)

/**
 * Counts ballots into standings, a pick at each position worth what [points] says.
 *
 * A candidate's points are every pick of it, whichever track the pick named, so a vote split across a
 * candidate's tracks still counts for the candidate. Ties break on more first places, then more voters, then
 * more second places, third, and so on; candidates still level share a place, and the next place skips
 * as many as shared it. A merged track counts as the track [canonical] resolves it to.
 */
object Tally {
    fun count(
        ballots: List<List<CountedPick>>,
        points: List<Int>,
        canonical: (Long) -> Long = { it },
    ): List<Standing> {
        val candidates = mutableMapOf<Long, CandidateCount>()
        for (ballot in ballots) {
            for (pick in ballot) {
                val worth = points.getOrNull(pick.position - 1) ?: continue
                candidates
                    .getOrPut(pick.candidateId) { CandidateCount(pick.candidateId, points.size) }
                    .add(pick.position, pick.trackId?.let(canonical), worth)
            }
        }
        val order = orderFor(points.size)
        val ordered = candidates.values.sortedWith(order.thenBy { it.candidateId })
        val standings = mutableListOf<Standing>()
        ordered.forEachIndexed { index, candidate ->
            val previous = ordered.getOrNull(index - 1)
            val place = if (previous != null && order.compare(previous, candidate) == 0) standings.last().place else index + 1
            standings += candidate.standing(place)
        }
        return standings
    }

    // Best first: more points, more first places, more voters, then each later position in turn.
    private fun orderFor(positions: Int): Comparator<CandidateCount> =
        (1 until positions).fold(
            compareByDescending<CandidateCount> { it.points }
                .thenByDescending { it.byPosition[0] }
                .thenByDescending { it.voters },
        ) { order, position -> order.thenByDescending { it.byPosition[position] } }

    private class CandidateCount(
        val candidateId: Long,
        positions: Int,
    ) {
        var points = 0
        var voters = 0
        val byPosition = IntArray(positions)
        private val tracks = mutableMapOf<Long, TrackCount>()

        fun add(
            position: Int,
            trackId: Long?,
            worth: Int,
        ) {
            points += worth
            voters += 1
            byPosition[position - 1] += 1
            if (trackId != null) tracks.getOrPut(trackId) { TrackCount(trackId) }.add(worth)
        }

        // The track with the most points; then the one more ballots named; then the one added first.
        fun standing(place: Int): Standing {
            val track =
                tracks.values.minWithOrNull(
                    compareByDescending<TrackCount> { it.points }.thenByDescending { it.voters }.thenBy { it.trackId },
                )
            return Standing(place, candidateId, track?.trackId, points, voters, byPosition.toList())
        }
    }

    private class TrackCount(
        val trackId: Long,
    ) {
        var points = 0
        var voters = 0

        fun add(worth: Int) {
            points += worth
            voters += 1
        }
    }
}
