package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE users
            SET username = :username,
                email = :email,
                initials = :initials,
                first_name = :firstName,
                prefix = :prefix,
                last_name = :lastName,
                phone_number = :phoneNumber,
                discord = :discord,
                newsletter = :newsletter,
                enabled = :enabled,
                contact_id = NULL,
                deleted_at = :deletedAt,
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id
              AND deleted_at = '9999-12-31 23:59:59'
        """,
        nativeQuery = true
    )
    fun markDeletedAndPseudonymized(
        @Param("id") id: Long,
        @Param("username") username: String,
        @Param("email") email: String,
        @Param("initials") initials: String,
        @Param("firstName") firstName: String,
        @Param("prefix") prefix: String?,
        @Param("lastName") lastName: String,
        @Param("phoneNumber") phoneNumber: String?,
        @Param("discord") discord: String?,
        @Param("newsletter") newsletter: Boolean,
        @Param("enabled") enabled: Boolean,
        @Param("deletedAt") deletedAt: Instant,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE users
            SET username = :username,
                email = :email,
                initials = :initials,
                first_name = :firstName,
                prefix = :prefix,
                last_name = :lastName,
                phone_number = :phoneNumber,
                discord = :discord,
                newsletter = :newsletter,
                enabled = :enabled,
                contact_id = NULL,
                deleted_at = '9999-12-31 23:59:59',
                updated_at = :updatedAt,
                version = version + 1
            WHERE id = :id
              AND deleted_at <> '9999-12-31 23:59:59'
        """,
        nativeQuery = true
    )
    fun restoreFromDeletedSnapshot(
        @Param("id") id: Long,
        @Param("username") username: String,
        @Param("email") email: String,
        @Param("initials") initials: String,
        @Param("firstName") firstName: String,
        @Param("prefix") prefix: String?,
        @Param("lastName") lastName: String,
        @Param("phoneNumber") phoneNumber: String?,
        @Param("discord") discord: String?,
        @Param("newsletter") newsletter: Boolean,
        @Param("enabled") enabled: Boolean,
        @Param("updatedAt") updatedAt: Instant
    ): Int
}
