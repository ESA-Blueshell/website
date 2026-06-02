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
}
