package net.blueshell.api.game.api

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A game as the esports seed files list it, with the code the files give it. */
data class ShippedGame(
    val code: String,
    val name: String,
    val slug: String,
    val accent: String?,
    val sortIndex: Int,
    val intro: String?,
    val archived: Boolean,
)

/**
 * What the seeds of other modules ask of games, which live in this module.
 *
 * Unlike [GameService.create], a shipped game keeps the code the files give it, and a game the
 * database ever held under that code, removed ones included, is never written again.
 */
@Service
class ShippedGames(
    private val games: GameRepository,
) {
    /** Whether a game holds [code] now, which a seed that links to one waits for. */
    @Transactional(readOnly = true)
    fun stands(code: String): Boolean = games.findByCode(code) != null

    @Transactional(readOnly = true)
    fun everHeld(code: String): Boolean = games.findByCode(code) != null || games.findRemovedIdByCode(code) != null

    @Transactional
    fun add(game: ShippedGame) {
        games.save(
            Game(
                code = game.code,
                name = game.name,
                slug = game.slug,
                accent = game.accent,
                sortIndex = game.sortIndex,
                intro = game.intro,
                archived = game.archived,
            ),
        )
    }

    /** Archives a standing game; one removed since stays as it is. */
    @Transactional
    fun archive(code: String) {
        games.findByCode(code)?.archived = true
    }
}
