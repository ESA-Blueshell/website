package net.blueshell.api.game.api

/**
 * Implemented by the module that fields teams, which is the only one that knows which games are
 * played in competition now. Derived there and asked here, never stored on the game.
 */
fun interface GamesInCompetition {
    /** The codes of the games fielded in the current season. */
    fun currentlyFielded(): Set<String>
}
