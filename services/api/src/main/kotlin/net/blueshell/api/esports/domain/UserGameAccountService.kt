package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.esports.persistence.UserGameAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserGameAccountService(
    private val accounts: UserGameAccountRepository,
    private val games: GameService,
) {
    @Transactional(readOnly = true)
    fun findAllForUser(userId: Long): List<UserGameAccount> = accounts.findAllByUserId(userId)

    /** Handles for one game, keyed by member, for resolving a whole roster in one query. */
    @Transactional(readOnly = true)
    fun handlesFor(game: String, userIds: Collection<Long>): Map<Long, String> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            accounts.findAllByGameAndUserIdIn(game, userIds).associate { it.userId to it.handle }
        }

    /** One handle per member per game, so setting it again replaces rather than accumulates. */
    @Transactional
    fun set(userId: Long, game: String, handle: String): UserGameAccount {
        val trimmed = handle.trim()
        require(trimmed.isNotBlank()) { "A handle cannot be blank" }
        // A handle is a handle in some game, so a code naming none is refused with a reason
        // rather than reaching the foreign key.
        games.requireGame(game)
        val existing = accounts.findByUserIdAndGame(userId, game)
        if (existing == null) {
            return accounts.save(UserGameAccount(userId = userId, game = game, handle = trimmed))
        }
        existing.handle = trimmed
        return accounts.save(existing)
    }

    @Transactional
    fun clear(userId: Long, game: String) {
        accounts.findByUserIdAndGame(userId, game)?.let { accounts.delete(it) }
    }
}
