package net.blueshell.api.game.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
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

    /** A game the board adds from the casual pages. Its address answers straight away. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCasualGame(
        @Valid @RequestBody request: CasualGameRequest,
    ): CasualGameResponse =
        answer(
            games.create(
                name = request.name,
                slug = request.slug,
                intro = request.intro,
                accent = request.accent,
                banner = request.banner,
                icon = request.icon,
                channels = request.channelsAsked(),
            ),
        )

    /** A game corrected: everything but its code, which everything else points at. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/{game}")
    fun updateCasualGame(
        @PathVariable game: String,
        @Valid @RequestBody request: CasualGameRequest,
    ): CasualGameResponse =
        answer(
            games.update(
                game = game,
                name = request.name,
                slug = request.slug,
                intro = request.intro,
                accent = request.accent,
                banner = request.banner,
                icon = request.icon,
                sortIndex = null,
                channels = request.channelsAsked(),
            ),
        )

    /** A game archived, or back among the games played. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'write')")
    @PutMapping("/{game}/archived")
    fun archiveGame(
        @PathVariable game: String,
        @RequestBody request: ArchiveGameRequest,
    ): CasualGameResponse = answer(games.archive(game, request.archived))

    /** What removing a game would touch, read before the board is asked to agree to it. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @GetMapping("/{game}/holdings")
    fun findGameHoldings(
        @PathVariable game: String,
    ): GameHoldingsResponse {
        val held = games.heldAgainst(game)
        return GameHoldingsResponse(
            channels = held["channels"] ?: 0,
            committees = held["committees"] ?: 0,
            events = held["events"] ?: 0,
            teams = held["teams"] ?: 0,
            players = held["players"] ?: 0,
        )
    }

    /** An archived game taken off the site; its row is kept. */
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Team', 'delete')")
    @DeleteMapping("/{game}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeGame(
        @PathVariable game: String,
    ) = games.remove(game)

    private fun CasualGameRequest.channelsAsked() = channels?.map { GameChannel(it.id, it.guildId, it.name) }

    private fun answer(game: Game) = game.asCasualResponse(games.inCompetition().contains(game.code))
}
