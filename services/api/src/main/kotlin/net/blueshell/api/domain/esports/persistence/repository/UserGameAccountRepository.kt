package net.blueshell.api.domain.esports.persistence.repository

import net.blueshell.api.domain.esports.persistence.UserGameAccount
import net.blueshell.api.shared.enums.Game
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGameAccountRepository : JpaRepository<UserGameAccount, Long> {
    fun findAllByUserId(userId: Long): List<UserGameAccount>

    fun findByUserIdAndGame(userId: Long, game: Game): UserGameAccount?

    fun findAllByGameAndUserIdIn(game: Game, userIds: Collection<Long>): List<UserGameAccount>
}
