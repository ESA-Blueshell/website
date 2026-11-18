package net.blueshell.api.vote.persistence

/**
 * What an annual vote asks for and how it counts. Each kind runs at most one vote a year, and one
 * the api does not offer is refused before any vote of it is started, read or cast.
 */
enum class VoteKind(
    /** How many picks a ballot holds unless the board sets another number for the vote. */
    val defaultPicks: Int,
    /** The last place the results keep; every candidate tied at it is kept. */
    val topSize: Int,
    /** Whether a pick names one of the game's tracks as well as the game. */
    val picksTrack: Boolean,
    /** Whether only games first released in the vote's year may be picked. */
    val releasedInYear: Boolean,
    /** Whether the api takes the kind at all. TWIN: `VOTE_KIND_PAGES` in the frontend's `kinds.ts`. */
    val offered: Boolean,
) {
    OST_TOP_50(defaultPicks = 5, topSize = 50, picksTrack = true, releasedInYear = false, offered = true),

    // The picks and the size of the top are the board's to settle; these stand in until they do.
    GAME_OF_THE_YEAR(defaultPicks = 5, topSize = 10, picksTrack = false, releasedInYear = true, offered = false),
}
