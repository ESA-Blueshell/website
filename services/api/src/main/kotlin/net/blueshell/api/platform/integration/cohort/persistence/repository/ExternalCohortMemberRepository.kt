package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.ExternalCohortMember
import net.blueshell.api.platform.integration.cohort.persistence.ExternalCohortMemberId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExternalCohortMemberRepository : JpaRepository<ExternalCohortMember, ExternalCohortMemberId> {

    @Query("SELECT e FROM ExternalCohortMember e WHERE e.id.cohortId = :cohortId")
    fun findAllByCohortId(@Param("cohortId") cohortId: Long): List<ExternalCohortMember>

    @Modifying
    @Query("DELETE FROM ExternalCohortMember e WHERE e.id.cohortId = :cohortId AND e.id.externalUserId NOT IN :externalUserIds")
    fun deleteStaleRows(@Param("cohortId") cohortId: Long, @Param("externalUserIds") externalUserIds: Collection<String>)

    @Modifying
    @Query("DELETE FROM ExternalCohortMember e WHERE e.id.cohortId = :cohortId")
    fun deleteAllByCohortId(@Param("cohortId") cohortId: Long)

    @Modifying
    @Query("DELETE FROM ExternalCohortMember e WHERE e.id.cohortId = :cohortId AND e.id.externalUserId = :externalUserId")
    fun deleteRow(@Param("cohortId") cohortId: Long, @Param("externalUserId") externalUserId: String)
}
