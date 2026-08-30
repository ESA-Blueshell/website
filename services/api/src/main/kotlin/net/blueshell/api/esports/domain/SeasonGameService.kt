package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.SeasonGame
import net.blueshell.api.esports.persistence.SeasonGameRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Which games the association ran in which season.
 *
 * Entering a game is the board saying the association will play it; a team playing it is what
 * makes it public. The two are separate acts because they are separate decisions taken weeks
 * apart, and this is the record of the first.
 */
@Service
class SeasonGameService(
    private val entered: SeasonGameRepository,
    private val seasons: SeasonService,
    private val games: GamePageService,
    private val fielded: TeamSeasonService,
) {
    @Transactional(readOnly = true)
    fun gamesIn(seasonId: Long): Set<String> = entered.gamesIn(seasonId).toSet()

    /**
     * Records that a game runs in a season. Saying it twice says the same thing, so a repeat is
     * the row that is already there rather than a second one or a refusal.
     *
     * A game taken out and entered again revives the row it had. A second row would be refused
     * by the unique index, and reviving keeps the record of a game the association went back to
     * as one line rather than two.
     */
    @Transactional
    fun enter(seasonId: Long, game: String): SeasonGame {
        val code = games.requireGame(game).game
        entered.findBySeasonIdAndGame(seasonId, code)?.let { return it }
        entered.findDroppedId(seasonId, code)?.let { dropped ->
            entered.revive(dropped)
            entered.findBySeasonIdAndGame(seasonId, code)?.let { return it }
        }
        return entered.save(SeasonGame(season = seasons.findById(seasonId), game = code))
    }

    /**
     * Takes a game out of a season, which is only possible while it holds nothing.
     *
     * Refused while a team is still fielded in it, the way removing a game outright is refused
     * while it holds history: taking the game out would leave those teams in a season that does
     * not list the game they played. Unfielding them first is the order, and it is said rather
     * than left for the caller to work out.
     */
    @Transactional
    fun leave(seasonId: Long, game: String) {
        val code = games.requireGame(game).game
        val held = fielded.findByGameAndSeason(code, seasonId)
        if (held.isNotEmpty()) {
            val name = games.requireGame(code).name
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "$name still has ${held.size} team${if (held.size == 1) "" else "s"} in this season. " +
                    "Drop them from the season first, and the game can be taken out of it.",
            )
        }
        entered.findBySeasonIdAndGame(seasonId, code)?.let { entered.delete(it) }
    }
}
