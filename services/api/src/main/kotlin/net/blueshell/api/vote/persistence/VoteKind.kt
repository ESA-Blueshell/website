package net.blueshell.api.vote.persistence

// Five ranked picks, the first worth the most, as Eurovision scores its top.
private val FIVE_RANKED = listOf(12, 10, 8, 6, 4)

/**
 * What an annual vote asks for and how it counts. Each kind runs at most one vote a year.
 *
 * [points] holds what a pick at each position is worth, so its size is how many picks a ballot
 * holds. [topSize] is the last place the results keep: candidates tied at it are all kept, so the
 * results can run a row or two longer, rather than one being dropped by a rule nobody voted on.
 * [picksTrack] is whether a pick names one of the game's tracks as well as the game.
 * [releasedInYear] limits the games to those first released in the vote's year. [offered] is
 * whether the api takes the kind at all; one that is not is refused before any vote of it is
 * started, read or cast.
 */
enum class VoteKind(
    val points: List<Int>,
    val topSize: Int,
    val picksTrack: Boolean,
    val releasedInYear: Boolean,
    val offered: Boolean,
) {
    OST_TOP_50(points = FIVE_RANKED, topSize = 50, picksTrack = true, releasedInYear = false, offered = true),

    // The picks and the size of the top are the board's to settle; these stand in, and the kind is
    // refused, until they do. The frontend's `GAME_OF_THE_YEAR.enabled` in `kinds.ts` follows this.
    GAME_OF_THE_YEAR(points = FIVE_RANKED, topSize = 10, picksTrack = false, releasedInYear = true, offered = false),
    ;

    val picksPerBallot: Int get() = points.size
}
