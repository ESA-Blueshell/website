package net.blueshell.api.game.api

/**
 * Implemented by a module that holds something against a game: the teams fielded in it, the
 * events that name it, the committees linked to it.
 *
 * Asked before a game is removed, so the module that knows what it holds says what the removal
 * touches and decides whether that stops it. This module never learns what a team or an event is.
 */
interface GameHoldings {
    /** What this module holds against [code], by what it is called on screen, such as `teams`. */
    fun heldAgainst(code: String): Map<String, Long> = emptyMap()

    /** Refuses, by throwing, when what this module holds against [code] stops its removal. */
    fun refuseRemoval(code: String) = Unit
}
