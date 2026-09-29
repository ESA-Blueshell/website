package net.blueshell.api.game.api

/**
 * What removing a game would touch, one count per kind of thing held against it.
 *
 * Each module fills the counts it knows and leaves the rest at nothing, so a new kind is a new
 * field rather than a string every reader has to spell the same way.
 */
data class GameHeld(
    val channels: Long = 0,
    val committees: Long = 0,
    val events: Long = 0,
    val teams: Long = 0,
    val players: Long = 0,
) {
    operator fun plus(other: GameHeld): GameHeld =
        GameHeld(
            channels = channels + other.channels,
            committees = committees + other.committees,
            events = events + other.events,
            teams = teams + other.teams,
            players = players + other.players,
        )
}
