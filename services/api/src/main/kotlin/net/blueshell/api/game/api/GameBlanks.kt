package net.blueshell.api.game.api

import net.blueshell.api.file.persistence.File
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import net.blueshell.api.game.persistence.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Fills in what a game has left blank: its pictures from the art the site ships, its channels
 * from the Discord server. For the modules that seed and match, and never a way to replace a
 * choice somebody made.
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

    /** Gives [code] a banner where it has none, and answers whether it did; [banner] is stored only then. */
    @Transactional
    fun fillBanner(
        code: String,
        banner: () -> File,
    ): Boolean {
        val game = games.findByCode(code) ?: return false
        if (game.banner != null) return false
        game.banner = banner()
        return true
    }

    /** Gives [code] an icon where it has none, and answers whether it did; [icon] is stored only then. */
    @Transactional
    fun fillIcon(
        code: String,
        icon: () -> File,
    ): Boolean {
        val game = games.findByCode(code) ?: return false
        if (game.icon != null) return false
        game.icon = icon()
        return true
    }

    private fun game(code: String): Game = games.findByCode(code.trim()) ?: throw UnknownGameCode(code)
}

private fun Game.channelsOf(kind: GameChannelKind): MutableList<GameChannel> =
    when (kind) {
        GameChannelKind.CASUAL -> channels
        GameChannelKind.COMPETITION -> esportsChannels
    }
