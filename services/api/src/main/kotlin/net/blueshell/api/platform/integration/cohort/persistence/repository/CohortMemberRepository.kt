package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortMemberRepository : BaseRepository<CohortMember, Long> {

    // ── Desired rows (userId != null) ─────────────────────────────────────────

    fun findAllByUserIdAndUserIdIsNotNull(userId: Long): List<CohortMember>

    fun findAllByCohortIdAndUserIdIsNotNull(cohortId: Long): List<CohortMember>

    fun findAllBySubjectIdAndUserIdIsNotNull(subjectId: Long): List<CohortMember>

    fun findByCohortIdAndUserId(cohortId: Long, userId: Long): CohortMember?

    // ── Stranger rows (userId == null, observedAt != null) ────────────────────

    fun findAllByCohortIdAndUserIdIsNull(cohortId: Long): List<CohortMember>

    fun findByCohortIdAndExternalUserIdAndUserIdIsNull(
        cohortId: Long,
        externalUserId: String,
    ): CohortMember?

    // ── All rows (desired + stranger) ─────────────────────────────────────────

    /** All active rows — use sparingly; prefer desired-only or stranger-only. */
    fun findAllByCohortId(cohortId: Long): List<CohortMember>

    fun findAllBySubjectId(subjectId: Long): List<CohortMember>
}
