package net.blueshell.api.esports.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.esports.domain.UserGameAccountService
import net.blueshell.api.shared.enums.Game
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * What a member is called in each game.
 *
 * Held per member rather than per roster entry, so a member who changes their handle changes
 * it on every season they ever played. A member edits their own; the board edits anybody's,
 * which is what the user permission already means.
 */
@RestController
@RequestMapping("/users/{userId}/game-accounts")
@Tag(name = "Game accounts", description = "Per-member, per-game handles")
class GameAccountController(
    private val accounts: UserGameAccountService,
) {
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    @GetMapping
    fun findGameAccounts(@PathVariable userId: Long): List<GameAccountResponse> =
        accounts.findAllForUser(userId).map { it.asResponse() }

    @PreAuthorize("hasPermission(#userId, 'User', 'write')")
    @PutMapping("/{game}")
    fun setGameAccount(
        @PathVariable userId: Long,
        @PathVariable game: Game,
        @Valid @RequestBody request: GameAccountRequest,
    ): GameAccountResponse = accounts.set(userId, game, request.handle).asResponse()

    @PreAuthorize("hasPermission(#userId, 'User', 'write')")
    @DeleteMapping("/{game}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearGameAccount(@PathVariable userId: Long, @PathVariable game: Game) {
        accounts.clear(userId, game)
    }
}
