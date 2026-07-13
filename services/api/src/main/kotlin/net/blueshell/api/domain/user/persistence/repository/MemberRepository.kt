package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUser_Id(userId: Long): Boolean
    fun existsByUser_IdAndEndDateIsNull(userId: Long): Boolean
    fun findByUser_Id(userId: Long): MutableList<Membership>

    // Native queries deliberately bypass the entity's @SQLRestriction (which pins
    // every managed/Criteria query to deleted_at = sentinel), so they can read and
    // restore soft-deleted rows. The sentinel mirrors SoftDeleteSentinels
    // .ACTIVE_ROW_DELETED_AT ('9999-12-31 23:59:59'); annotation values must be
    // compile-time constants, so it is centralised here as a String literal const.

    @Query(value = "SELECT * FROM memberships WHERE user_id = :userId AND deleted_at <> " + SENTINEL, nativeQuery = true)
    fun findDeletedByUser_Id(@Param("userId") userId: Long): MutableList<Membership>

    @Query(value = "SELECT * FROM memberships WHERE id = :id AND deleted_at <> " + SENTINEL, nativeQuery = true)
    fun findDeletedById(@Param("id") id: Long): Membership?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "UPDATE memberships SET deleted_at = " + SENTINEL + ", version = version + 1 " +
            "WHERE id = :id AND deleted_at <> " + SENTINEL,
        nativeQuery = true
    )
    fun restoreById(@Param("id") id: Long): Int

    companion object {
        /** SQL literal for the not-deleted sentinel; see SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT. */
        private const val SENTINEL = "'9999-12-31 23:59:59'"
    }
}
