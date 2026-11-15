package net.blueshell.api.vote.persistence

// Five ranked picks, the first worth the most, as Eurovision scores its top.
private val FIVE_RANKED = listOf(12, 10, 8, 6, 4)

/**
 * What an annual vote asks for and how it counts. Each kind runs at most one vote a year.
 *
 * [points] holds what a pick at each position is worth, so its size is how many picks a ballot
 * holds. [picksTrack] is whether a pick names one of the game's tracks as well as the game.
 * [releasedInYear] limits the games to those first released in the vote's year.
 */
enum class VoteKind(
    val points: List<Int>,
    val topSize: Int,
    val picksTrack: Boolean,
    val releasedInYear: Boolean,
) {
    OST_TOP_50(points = FIVE_RANKED, topSize = 50, picksTrack = true, releasedInYear = false),

    // The picks and the size of the top are the board's to settle; these stand in until they do.
    GAME_OF_THE_YEAR(points = FIVE_RANKED, topSize = 10, picksTrack = false, releasedInYear = true),
    ;

    val picksPerBallot: Int get() = points.size
}
