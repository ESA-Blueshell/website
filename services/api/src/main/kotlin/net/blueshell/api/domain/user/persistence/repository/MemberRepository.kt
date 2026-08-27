package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
@Suppress("FunctionName")
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUser_Id(userId: Long): Boolean
    fun existsByUser_IdAndEndDateIsNull(userId: Long): Boolean
    fun findByUser_Id(userId: Long): MutableList<Membership>

    /**
     * Everybody whose membership overlapped the window, whatever kind of membership it was.
     * A membership with no end date is still running, so it overlaps anything from its start
     * onwards — including a stretch of time that has not arrived yet.
     *
     * This is the rule the user manager's "member in period" column draws, spelled once more
     * here for the association to ask the same question of itself. Changing one means
     * changing the other: see `overlapsContributionPeriod` in the frontend.
     */
    @Query(
        """
        SELECT DISTINCT m.user.id FROM Membership m
        WHERE m.startDate <= :to AND (m.endDate IS NULL OR m.endDate >= :from)
        """,
    )
    fun findUserIdsOverlapping(@Param("from") from: LocalDate, @Param("to") to: LocalDate): List<Long>

    @Query(
        """
        SELECT COUNT(m) > 0 FROM Membership m
        WHERE m.user.id = :userId
          AND m.startDate <= :to AND (m.endDate IS NULL OR m.endDate >= :from)
        """,
    )
    fun existsOverlapping(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): Boolean

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
