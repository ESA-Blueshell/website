package net.blueshell.api.game.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.game.api.GameService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The games as the casual pages read them, and the board's edits from those pages.
 *
 * Reading is public; every write is the board's, under the same evaluator that guards the rest of
 * competition, because the same people edit both.
 */
@RestController
@RequestMapping("/games")
@Tag(name = "Games", description = "The games the association plays, casually or in competition")
class GameController(
    private val games: GameService,
) {
    /** Every game, archived ones included, in the order they are shown. */
    @PermitAll
    @GetMapping
    fun findCasualGames(): List<CasualGameResponse> {
        val fielded = games.inCompetition()
        return games.findAll().map { it.asCasualResponse(fielded.contains(it.code)) }
    }
}
