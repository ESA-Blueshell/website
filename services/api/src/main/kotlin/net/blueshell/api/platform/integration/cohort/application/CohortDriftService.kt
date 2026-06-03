package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset

/**
 * DB-only drift read: classifies [CohortMember] ledger rows for one
 * (subject, system) pair. No outbound port calls — the live external
 * fetch happens in the reconcile job, not here.
 *
 * - Missing: desired rows (`userId != null`) with null `observedAt`.
 * - Extra:   stranger rows (`userId == null`) with non-null `observedAt`.
 * - `lastReconciledAt`: max `observedAt` across all ledger rows.
 */
@Service
@Transactional(readOnly = true)
class CohortDriftService(
    private val cohortRepo: CohortRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val users: UserService,
) : CohortDrift {

    override fun compute(subjectId: Long, system: TargetSystem): DriftReport {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No $system mapping for subject $subjectId")

        val externalCohortId = externalIds.find(COHORT_AGGREGATE, cohort.id!!, system.name)?.externalId
            ?: return DriftReport.notMaterialised(cohort.id!!, system)

        val allRows = memberRepo.findAllByCohortId(cohort.id!!)

        // Missing: desired rows not yet confirmed present externally.
        val missingRows = allRows.filter { it.userId != null && it.observedAt == null }
        val missingUserIds = missingRows.mapNotNull { it.userId }.toSet()
        val missingMappedUserIds = externalIds.findBatch(USER_AGGREGATE, missingUserIds, system.name)
            .map { it.aggregateId }
            .toSet()
        val missing = missingRows.map { row ->
            MissingRow(
                userId = row.userId!!,
                hasExternalMapping = row.userId in missingMappedUserIds,
            )
        }

        // Extras: stranger rows present externally but not desired locally.
        val strangerRows = allRows.filter { it.userId == null && it.observedAt != null }
        val strangerExtIds = strangerRows.mapNotNull { it.externalUserId }.toSet()
        val ownerMappings = externalIds.findByExternalIds(USER_AGGREGATE, system.name, strangerExtIds)
            .associateBy { it.externalId }

        val knownUserIds = ownerMappings.values.map { it.aggregateId }.distinct()
        val userById = if (knownUserIds.isNotEmpty()) {
            users.findAllByIds(knownUserIds).associateBy { requireNotNull(it.id) }
        } else {
            emptyMap()
        }

        val missingActiveUserIds = knownUserIds.toSet() - userById.keys
        val softDeletedUserIds = users.findSoftDeletedIds(missingActiveUserIds)

        val extras = strangerRows.map { row ->
            val extId = row.externalUserId ?: return@map ExtraRow.UnknownExternal(
                externalUserId = "",
                label = row.label,
            )
            val owner = ownerMappings[extId]
            if (owner != null) {
                val user = userById[owner.aggregateId]
                val softDeleted = owner.aggregateId in softDeletedUserIds
                ExtraRow.KnownLocalUser(
                    externalUserId = extId,
                    label = row.label,
                    userId = owner.aggregateId,
                    fullName = user?.fullName,
                    email = user?.email,
                    softDeleted = softDeleted,
                )
            } else {
                ExtraRow.UnknownExternal(externalUserId = extId, label = row.label)
            }
        }

        val lastReconciledAt = allRows
            .mapNotNull { it.observedAt }
            .maxOfOrNull { it }
            ?.toInstant(ZoneOffset.UTC)

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
