package net.blueshell.api.game.api

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.game.persistence.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Fills in the channels a game has left blank from the Discord server. For the module that
 * matches them, and never a way to replace a choice somebody made.
 */
@Service
class GameBlanks(
    private val games: GameRepository,
) {
    /** Whether [code] has any Discord channels of [kind] yet. */
    @Transactional(readOnly = true)
    fun hasChannels(
        code: String,
        kind: GameChannelKind,
    ): Boolean = game(code).channelsOf(kind).isNotEmpty()

    /** Adds [channels] of [kind] to the ones [code] has, leaving out any it has already, and answers how many it added. */
    @Transactional
    fun addChannels(
        code: String,
        kind: GameChannelKind,
        channels: List<GameChannel>,
    ): Int {
        val held = game(code).channelsOf(kind)
        val added = channels.distinctBy(GameChannel::channelId).filter { offered -> held.none { it.channelId == offered.channelId } }
        held.addAll(added)
        return added.size
    }

    private fun game(code: String): Game = games.findByCode(code.trim()) ?: throw UnknownGameCode(code)
}

private fun Game.channelsOf(kind: GameChannelKind): MutableList<GameChannel> =
    when (kind) {
        GameChannelKind.CASUAL -> channels
        GameChannelKind.COMPETITION -> esportsChannels
    }
