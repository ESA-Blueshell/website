package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : BaseRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>

    /** Ids of accounts that have not been activated. Soft-deleted rows are excluded by the entity. */
    @Query("select u.id from User u where u.enabled = false")
    fun findIdsByEnabledFalse(): List<Long>

    /** Everybody who opted into the newsletter, activated account or not. */
    @Query("SELECT u.id FROM User u WHERE u.newsletter = true")
    fun findIdsByNewsletterTrue(): List<Long>

    /**
     * `findAllById` with the profile fetched alongside. `User.memberProfile` is an eager
     * `mappedBy` one-to-one, so a lazily joined member costs an extra query each.
     */
    @Query(
        """
        SELECT u FROM User u
        LEFT JOIN FETCH u.memberProfile
        WHERE u.id IN :ids
        """,
    )
    fun findAllByIdsWithProfiles(@Param("ids") ids: Collection<Long>): List<User>

    fun existsByUsername(username: String): Boolean

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByDiscord(discord: String): Boolean

    fun existsByDiscordAndIdNot(discord: String, id: Long): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByPhoneNumberAndIdNot(phoneNumber: String, id: Long): Boolean

    @Query(
        """
        SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
        FROM Membership m
        WHERE m.user.id = :userId
          AND m.endDate IS NULL
        """,
    )
    fun existsActiveMembershipByUserId(@Param("userId") userId: Long): Boolean

    /**
     * The id where the row exists at all, active or soft-deleted, so a caller can tell a user
     * who once existed from an id that never was. Native, to bypass `User`'s `@SQLRestriction`.
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

    /**
     * Active user ids greater than [afterId], ascending — keyset pagination for
     * bulk fan-out that does not hold one transaction (or one big result set)
     * across the whole user table. Respects the `@SQLRestriction` on [User].
     */
    @Query("SELECT u.id FROM User u WHERE u.id > :afterId ORDER BY u.id")
    fun findActiveIdsAfter(@Param("afterId") afterId: Long, pageable: Pageable): List<Long>
}
