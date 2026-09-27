package net.blueshell.api.game.api

import net.blueshell.api.game.persistence.GameChannel

/**
 * What a game's competition pages carry apart from its casual ones: their own intro, blank to say
 * what the casual pages say, and the channels its esports players meet in, null to keep them.
 */
data class GameCompetition(
    val intro: String? = null,
    val channels: List<GameChannel>? = null,
)
