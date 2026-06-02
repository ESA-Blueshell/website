package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortMemberRepository : BaseRepository<CohortMember, Long> {
    fun findAllByUserId(userId: Long): List<CohortMember>

    fun findAllByCohortId(cohortId: Long): List<CohortMember>

    fun findByCohortIdAndUserId(cohortId: Long, userId: Long): CohortMember?

    fun findAllBySubjectId(subjectId: Long): List<CohortMember>
}
