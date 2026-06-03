package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
@Transactional(readOnly = true)
class CohortDriftService(
    private val cohortRepo: CohortRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
    private val registry: CohortPortRegistry,
) : CohortDrift {

    override fun compute(subjectId: Long, system: TargetSystem): DriftReport {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No $system mapping for subject $subjectId")

        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohort.id!!, system.name)?.externalId
            ?: return DriftReport.notMaterialised(cohort.id!!, system)

        // External state — keyed by externalUserId
        val externalByUserId = registry.require(system)
            .listMembers(externalCohortId)
            .associateBy { it.externalUserId }

        // Desired local state
        val desiredUserIds = memberRepo.findAllByCohortId(cohort.id!!)
            .map { it.userId }
            .toSet()

        // Map desired userIds → their externalUserId on this system
        val desiredExternalById = externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .associate { it.aggregateId to it.externalId }

        val desiredExternalIds = desiredExternalById.values.filterNotNull().toSet()

        // Extras: external members not in the desired set
        val extras = externalByUserId
            .filterKeys { it !in desiredExternalIds }
            .map { (extId, ref) ->
                val ownerMapping = externalIds.findOwner(USER_AGGREGATE, system.name, extId)
                if (ownerMapping != null) {
                    val user = users.findAllByIds(listOf(ownerMapping.aggregateId)).firstOrNull()
                    val softDeleted = user == null && users.isSoftDeleted(ownerMapping.aggregateId)
                    ExtraRow.KnownLocalUser(
                        externalUserId = extId,
                        label = ref.label,
                        userId = ownerMapping.aggregateId,
                        fullName = user?.fullName,
                        email = user?.email,
                        softDeleted = softDeleted,
                    )
                } else {
                    ExtraRow.UnknownExternal(externalUserId = extId, label = ref.label)
                }
            }

        // Missing: desired users whose external id is absent from the external set
        val missing = desiredUserIds
            .filter { userId ->
                val extId = desiredExternalById[userId]
                extId == null || extId !in externalByUserId
            }
            .map { userId ->
                MissingRow(userId = userId, hasExternalMapping = desiredExternalById[userId] != null)
            }

        return DriftReport(
            cohortId = cohort.id!!,
            system = system,
            externalCohortId = externalCohortId,
            extras = extras,
            missing = missing,
        )
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
        private const val USER_AGGREGATE = "USER"
    }
}
