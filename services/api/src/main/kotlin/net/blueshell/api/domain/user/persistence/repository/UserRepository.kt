package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : BaseRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>

    fun existsByUsername(username: String): Boolean

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByDiscord(discord: String): Boolean

    fun existsByDiscordAndIdNot(discord: String, id: Long): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByPhoneNumberAndIdNot(phoneNumber: String, id: Long): Boolean

    /**
     * Native query that bypasses the @SQLRestriction on User so callers can
     * tell "this user existed at some point" apart from "this id was never
     * valid". Returns the id when the row exists in the users table (active
     * or soft-deleted) and null otherwise. Used by the cohort engine to
     * preserve cohort_member rows for historical stats when a user is
     * soft-deleted, rather than diff'ing them out of every cohort.
     */
    @Query(
        value = "SELECT id FROM users WHERE id = :userId AND deleted_at <> '9999-12-31 23:59:59.000000'",
        nativeQuery = true,
    )
    fun findSoftDeletedUserId(@Param("userId") userId: Long): Long?

    @Query(
        value = "SELECT id FROM users WHERE id IN (:userIds) AND deleted_at <> '9999-12-31 23:59:59.000000'",
        nativeQuery = true,
    )
    fun findSoftDeletedUserIds(@Param("userIds") userIds: Collection<Long>): List<Long>
}
