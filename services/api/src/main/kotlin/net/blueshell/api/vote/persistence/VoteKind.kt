package net.blueshell.api.vote.persistence

// Five ranked picks, the first worth the most, as Eurovision scores its top.
private val FIVE_RANKED = listOf(12, 10, 8, 6, 4)

/**
 * What an annual vote asks for and how it counts. Each kind runs at most one vote a year, and one
 * the api does not offer is refused before any vote of it is started, read or cast.
 */
enum class VoteKind(
    /** What a pick at each position is worth; its size is how many picks a ballot holds. */
    val points: List<Int>,
    /** The last place the results keep; every candidate tied at it is kept. */
    val topSize: Int,
    /** Whether a pick names one of the game's tracks as well as the game. */
    val picksTrack: Boolean,
    /** Whether only games first released in the vote's year may be picked. */
    val releasedInYear: Boolean,
    /** Whether the api takes the kind at all. TWIN: `VOTE_KIND_PAGES` in the frontend's `kinds.ts`. */
    val offered: Boolean,
) {
    OST_TOP_50(points = FIVE_RANKED, topSize = 50, picksTrack = true, releasedInYear = false, offered = true),

    // The picks and the size of the top are the board's to settle; these stand in until they do.
    GAME_OF_THE_YEAR(points = FIVE_RANKED, topSize = 10, picksTrack = false, releasedInYear = true, offered = false),
    ;

    val picksPerBallot: Int get() = points.size
}
