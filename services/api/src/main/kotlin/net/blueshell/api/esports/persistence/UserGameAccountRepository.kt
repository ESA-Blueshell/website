package net.blueshell.api.esports.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGameAccountRepository : JpaRepository<UserGameAccount, Long> {
    fun findAllByUserId(userId: Long): List<UserGameAccount>

    fun findByUserIdAndGame(userId: Long, game: String): UserGameAccount?

    fun findAllByGameAndUserIdIn(game: String, userIds: Collection<Long>): List<UserGameAccount>
}
