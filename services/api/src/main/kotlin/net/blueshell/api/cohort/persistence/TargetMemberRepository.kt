package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Rows come in two kinds: desired rows carry a `userId`, stranger rows carry none and a
 * `verifiedAt` instead. The finders say which kind they mean in their names.
 */
@Repository
interface TargetMemberRepository : BaseRepository<TargetMember, Long> {
    fun findAllByUserIdAndUserIdIsNotNull(userId: Long): List<TargetMember>

    fun findAllByTargetIdAndUserIdIsNotNull(targetId: Long): List<TargetMember>

    fun countByTargetIdAndUserIdIsNotNull(targetId: Long): Long

    fun findAllByCohortIdAndUserIdIsNotNull(cohortId: Long): List<TargetMember>

    fun countByCohortIdAndUserIdIsNotNull(cohortId: Long): Long

    fun findByTargetIdAndUserId(
        targetId: Long,
        userId: Long,
    ): TargetMember?

    fun findAllByTargetIdAndUserIdIsNull(targetId: Long): List<TargetMember>

    fun findByTargetIdAndExternalUserIdAndUserIdIsNull(
        targetId: Long,
        externalUserId: String,
    ): TargetMember?

    fun findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(
        targetId: Long,
        externalUserIds: Collection<String>,
    ): List<TargetMember>

    /** All active rows — use sparingly; prefer desired-only or stranger-only. */
    fun findAllByTargetId(targetId: Long): List<TargetMember>

    fun findByTargetIdAndExternalUserIdAndUserIdIsNotNull(
        targetId: Long,
        externalUserId: String,
    ): TargetMember?

    fun findAllByCohortId(cohortId: Long): List<TargetMember>

    /** Per target, the people wanted on it and not pushed yet. TWIN: `TargetMember.state` is DESIRED. */
    @Query(
        "SELECT m.target.id AS targetId, COUNT(m) AS people FROM TargetMember m " +
            "WHERE m.userId IS NOT NULL AND m.syncedAt IS NULL AND m.verifiedAt IS NULL GROUP BY m.target.id",
    )
    fun countDesiredByTarget(): List<TargetCount>

    /** Per target, the people on it nobody here wants there. TWIN: `TargetMember.state` is STRANGER. */
    @Query(
        "SELECT m.target.id AS targetId, COUNT(m) AS people FROM TargetMember m " +
            "WHERE m.userId IS NULL AND m.externalUserId IS NOT NULL AND m.externalUserId <> '' AND m.verifiedAt IS NOT NULL " +
            "GROUP BY m.target.id",
    )
    fun countStrangersByTarget(): List<TargetCount>
}

/** How many ledger rows of one kind a target has. */
interface TargetCount {
    val targetId: Long
    val people: Long
}
