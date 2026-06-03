package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortRemediation
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CohortRemediationService(
    private val cohortRepo: CohortRepository,
    private val externalIds: ExternalIdMappingService,
    private val registry: CohortPortRegistry,
) : CohortRemediation {

    @Transactional
    override fun linkUser(userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping =
        externalIds.linkUser(userId, system, externalUserId)

    override fun removeExternalMember(cohortId: Long, externalUserId: String) {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            NonRetryableJobException("Cohort $cohortId not found")
        }
        val system = TargetSystem.valueOf(cohort.system)
        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId
            ?: throw NonRetryableJobException("Cohort $cohortId has no external id on $system — cannot remove member")
        registry.require(system).removeMember(externalUserId, externalCohortId)
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
    }
}
