package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.ExternalCohortMemberRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Reads drift from the shadow table ([ExternalCohortMemberRepository]).
 * No outbound port calls — the live external fetch happens in the
 * reconcile job, not here.
 */
@Service
@Transactional(readOnly = true)
class CohortDriftService(
    private val cohortRepo: CohortRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalMemberRepo: ExternalCohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
) : CohortDrift {

    override fun compute(subjectId: Long, system: TargetSystem): DriftReport {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No $system mapping for subject $subjectId")

        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohort.id!!, system.name)?.externalId
            ?: return DriftReport.notMaterialised(cohort.id!!, system)

        // Shadow table: last-observed external membership state.
        val shadow = externalMemberRepo.findAllByCohortId(cohort.id!!)
        val shadowByExtId = shadow.associateBy { it.id.externalUserId }

        // Desired local state.
        val desiredUserIds = memberRepo.findAllByCohortId(cohort.id!!)
            .map { it.userId }
            .toSet()

        // Map desired userIds → their externalUserId on this system.
        val desiredExternalById = externalIds
            .findBatch(USER_AGGREGATE, desiredUserIds, system.name)
            .associate { it.aggregateId to it.externalId }

        val desiredExternalIds = desiredExternalById.values.toSet()

        // Extras: shadow rows whose externalUserId is not in the desired set.
        val extras = shadowByExtId
            .filterKeys { it !in desiredExternalIds }
            .map { (extId, shadowRow) ->
                val ownerMapping = externalIds.findOwner(USER_AGGREGATE, system.name, extId)
                if (ownerMapping != null) {
                    val user = users.findAllByIds(listOf(ownerMapping.aggregateId)).firstOrNull()
                    val softDeleted = user == null && users.isSoftDeleted(ownerMapping.aggregateId)
                    ExtraRow.KnownLocalUser(
                        externalUserId = extId,
                        label = shadowRow.label,
                        userId = ownerMapping.aggregateId,
                        fullName = user?.fullName,
                        email = user?.email,
                        softDeleted = softDeleted,
                    )
                } else {
                    ExtraRow.UnknownExternal(externalUserId = extId, label = shadowRow.label)
                }
            }

        // Missing: desired users whose external id is absent from the shadow table.
        val missing = desiredUserIds
            .filter { userId ->
                val extId = desiredExternalById[userId]
                extId == null || extId !in shadowByExtId
            }
            .map { userId ->
                MissingRow(userId = userId, hasExternalMapping = desiredExternalById[userId] != null)
            }

        val lastReconciledAt = shadow
            .maxOfOrNull { it.observedAt }
            ?.let { java.time.ZoneOffset.UTC.let { tz -> it.toInstant(tz) } }

        return DriftReport(
            cohortId = cohort.id!!,
            system = system,
            externalCohortId = externalCohortId,
            extras = extras,
            missing = missing,
            lastReconciledAt = lastReconciledAt,
        )
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
        private const val USER_AGGREGATE = "USER"
    }
}
