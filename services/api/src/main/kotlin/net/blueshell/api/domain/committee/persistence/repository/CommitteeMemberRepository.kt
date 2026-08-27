package net.blueshell.api.domain.committee.persistence.repository

import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface CommitteeMemberRepository : BaseRepository<CommitteeMember, CommitteeMember.Id> {
    fun countByUser_Id(userId: Long): Long

    /** A user's current seats. Seats given up are filtered by the entity's restriction. */
    fun findByUser_Id(userId: Long): List<CommitteeMember>

    /**
     * Native query that bypasses the @SQLRestriction on CommitteeMember so the
     * caller can see the full join history. Returns `(committee_id, joined_at,
     * left_at)` triples; `left_at` carries the soft-delete sentinel
     * `9999-12-31 23:59:59` for currently-active memberships and the actual
     * removal timestamp for those that have ended. Used by the cohort engine
     * to compute period-overlap facts (ACTIVE_IN_PERIOD).
     */
    @Query(
        value = """
            SELECT committee_id, created_at, deleted_at
            FROM committee_members
            WHERE user_id = :userId
        """,
        nativeQuery = true,
    )
    fun findWindowsByUserId(@Param("userId") userId: Long): List<Array<Any>>

    /** Who sits on this committee now. A seat that has ended is filtered by the entity. */
    @Query("SELECT cm.user.id FROM CommitteeMember cm WHERE cm.committee.id = :committeeId")
    fun findUserIdsByCommitteeId(@Param("committeeId") committeeId: Long): List<Long>

    /**
     * Everybody who held a committee seat during the window, seats since given up included:
     * the native query reads past the entity's restriction, where `deleted_at` is when a seat
     * ended and the sentinel means it never did.
     */
    @Query(
        value = """
            SELECT DISTINCT user_id
            FROM committee_members
            WHERE created_at < :to AND deleted_at > :from
        """,
        nativeQuery = true,
    )
    fun findUserIdsWithSeatOverlapping(
        @Param("from") from: java.time.Instant,
        @Param("to") to: java.time.Instant,
    ): List<Long>
}
