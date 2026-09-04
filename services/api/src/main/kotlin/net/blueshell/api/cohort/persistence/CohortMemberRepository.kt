package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

/**
 * Rows come in two kinds: desired rows carry a `userId`, stranger rows carry none and a
 * `verifiedAt` instead. The finders say which kind they mean in their names.
 */
@Repository
interface CohortMemberRepository : BaseRepository<CohortMember, Long> {

    fun findAllByUserIdAndUserIdIsNotNull(userId: Long): List<CohortMember>

    fun findAllByCohortIdAndUserIdIsNotNull(cohortId: Long): List<CohortMember>

    fun countByCohortIdAndUserIdIsNotNull(cohortId: Long): Long

    fun findAllBySubjectIdAndUserIdIsNotNull(subjectId: Long): List<CohortMember>

    fun countBySubjectIdAndUserIdIsNotNull(subjectId: Long): Long

    fun findByCohortIdAndUserId(cohortId: Long, userId: Long): CohortMember?

    fun findAllByCohortIdAndUserIdIsNull(cohortId: Long): List<CohortMember>

    fun findByCohortIdAndExternalUserIdAndUserIdIsNull(
        cohortId: Long,
        externalUserId: String,
    ): CohortMember?

    fun findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(
        cohortId: Long,
        externalUserIds: Collection<String>,
    ): List<CohortMember>

    /** All active rows — use sparingly; prefer desired-only or stranger-only. */
    fun findAllByCohortId(cohortId: Long): List<CohortMember>

    fun findByCohortIdAndExternalUserIdAndUserIdIsNotNull(
        cohortId: Long,
        externalUserId: String,
    ): CohortMember?

    fun findAllBySubjectId(subjectId: Long): List<CohortMember>
}
