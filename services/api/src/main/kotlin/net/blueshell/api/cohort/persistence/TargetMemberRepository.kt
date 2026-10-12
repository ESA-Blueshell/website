package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
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
}
