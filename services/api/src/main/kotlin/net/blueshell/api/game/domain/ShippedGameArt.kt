package net.blueshell.api.game.domain

import net.blueshell.api.file.api.ShippedPictures
import net.blueshell.api.file.persistence.File
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedOrder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import kotlin.reflect.KMutableProperty1

/**
 * Puts the banner and icon `banners.csv` and `icons.csv` name on each game that has none.
 *
 * A game the files name and the database does not is skipped: the seed leaves a removed game
 * removed.
 */
@Component
class ShippedGameArt(
    private val pictures: ShippedPictures,
    private val games: GameRepository,
    /** Spring has no bean for this, so the shipped seed is the default. Tests pass their own. */
    private val seed: SeedCsv = GameSeed.files,
) {
    /** The pictures a run put on games, which is none at all on every start after the first. */
    data class Applied(
        val banners: Int,
        val icons: Int,
    )

    @Order(SeedOrder.ART)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        apply()
    }

    fun apply(): Applied =
        Applied(
            banners = pictures.ship(seed, "banners.csv", "banner", FileType.GAME_BANNER) { row, art -> fill(row, art, Game::banner) },
            icons = pictures.ship(seed, "icons.csv", "icon", FileType.GAME_ICON) { row, art -> fill(row, art, Game::icon) },
        )

    private fun fill(
        row: Map<String, String>,
        picture: () -> File,
        slot: KMutableProperty1<Game, File?>,
    ): Boolean {
        val game = games.findByCode(row.getValue("game")) ?: return false
        if (slot.get(game) != null) return false
        slot.set(game, picture())
        return true
    }
}
