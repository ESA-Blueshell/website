package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.persistence.UserGameAccount
import net.blueshell.api.domain.esports.persistence.repository.UserGameAccountRepository
import net.blueshell.api.shared.enums.Game
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserGameAccountService(
    private val accounts: UserGameAccountRepository,
) {
    @Transactional(readOnly = true)
    fun findAllForUser(userId: Long): List<UserGameAccount> = accounts.findAllByUserId(userId)

    /** Handles for one game, keyed by member, for resolving a whole roster in one query. */
    @Transactional(readOnly = true)
    fun handlesFor(game: Game, userIds: Collection<Long>): Map<Long, String> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            accounts.findAllByGameAndUserIdIn(game, userIds).associate { it.userId to it.handle }
        }

    /** One handle per member per game, so setting it again replaces rather than accumulates. */
    @Transactional
    fun set(userId: Long, game: Game, handle: String): UserGameAccount {
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A handle cannot be blank" }
        val existing = accounts.findByUserIdAndGame(userId, game)
        if (existing == null) {
            return accounts.save(UserGameAccount(userId = userId, game = game, handle = trimmed))
        }
        existing.handle = trimmed
        return accounts.save(existing)
    }

    @Transactional
    fun clear(userId: Long, game: Game) {
        accounts.findByUserIdAndGame(userId, game)?.let { accounts.delete(it) }
    }
}
