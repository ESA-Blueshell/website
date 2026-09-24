package net.blueshell.api.game.api

/**
 * Implemented by a module that holds something against a game, such as the teams fielded in it.
 *
 * Asked before a game is removed, so the module that knows what it holds decides whether that
 * stops the removal; this module never learns what a team or an event is.
 */
fun interface GameHoldings {
    /** Refuses, by throwing, when what this module holds against [code] stops its removal. */
    fun refuseRemoval(code: String)
}
