package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.platform.integration.cohort.persistence.CohortMemberState
import net.blueshell.api.platform.integration.cohort.persistence.needsPush
import net.blueshell.api.platform.integration.cohort.persistence.state
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortDrift
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.USER_AGGREGATE
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
 * - Missing: desired rows (`userId != null`) not yet pushed (`syncedAt == null`).
 * - Extra:   stranger rows (`userId == null`) confirmed externally (`verifiedAt != null`).
 * - `lastReconciledAt`: max `verifiedAt` across all ledger rows.
 */
@Service
@Transactional(readOnly = true)
class CohortDriftService(
    private val cohortRepo: CohortRepository,
    private val memberRepo: CohortMemberRepository,
    private val externalIds: ExternalIdMappingService,
    private val targetIds: CohortTargetIds,
    private val users: UserService,
) : CohortDrift {

    override fun compute(subjectId: Long, system: TargetSystem): DriftReport {
        val cohort = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No $system mapping for subject $subjectId")

        val externalCohortId = targetIds.find(cohort)
            ?: return DriftReport.notMaterialised(cohort.id!!, system)

        val allRows = memberRepo.findAllByCohortId(cohort.id!!)

        // Missing: desired rows not yet successfully pushed.
        val missingRows = allRows.filter { it.needsPush }
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
        val strangerRows = allRows.filter { it.state == CohortMemberState.STRANGER }
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
            val extId = row.externalUserId ?: return@map ExtraRow(
                externalUserId = "",
                label = row.label,
                kind = DriftExtraKind.UNKNOWN_EXTERNAL,
            )
            val owner = ownerMappings[extId]
            if (owner != null) {
                val user = userById[owner.aggregateId]
                val softDeleted = owner.aggregateId in softDeletedUserIds
                ExtraRow(
                    externalUserId = extId,
                    label = row.label,
                    kind = DriftExtraKind.KNOWN_LOCAL_USER,
                    userId = owner.aggregateId,
                    fullName = user?.fullName,
                    email = user?.email,
                    softDeleted = softDeleted,
                )
            } else {
                ExtraRow(
                    externalUserId = extId,
                    label = row.label,
                    kind = DriftExtraKind.UNKNOWN_EXTERNAL,
                )
            }
        }

        val lastReconciledAt = allRows
            .mapNotNull { it.verifiedAt }
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

}
