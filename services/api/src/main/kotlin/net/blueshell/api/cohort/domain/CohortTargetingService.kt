package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

/**
 * External target creation
 * runs outside any DB transaction; the local `Target` row and its external id
 * are persisted in a short write transaction afterwards so a provider failure
 * leaves no half-written row. [CohortTargetIds] owns every write of the id.
 */
@Service
class CohortTargetingService(
    private val targetRepo: TargetRepository,
    private val cohortRepo: CohortRepository,
    private val targetExternalIds: CohortTargetIds,
    private val strategies: TargetStrategies,
    private val jobs: JobQueue,
    transactionManager: PlatformTransactionManager,
) : CohortTargeting {
    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTransaction = TransactionTemplate(transactionManager)

    // Suspends any active transaction (e.g. the one AbstractJsonJobHandler opens
    // around deleteTarget / materialize) so provider HTTP calls hold no DB
    // connection — ADR-006/ADR-023.
    private val outsideTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    override fun linkExisting(
        cohortId: Long,
        system: TargetSystem,
        externalId: String,
    ): CohortTargetRow {
        resolveTarget(system, externalId)
        val linked =
            writeTransaction.execute {
                val cohort = requireCohort(cohortId)
                val existing = targetRepo.findByCohortIdAndSystem(cohortId, system.name)
                val target =
                    if (existing == null) {
                        targetRepo.save(newTarget(system, cohort.label, folder = null, cohortId = cohortId))
                    } else {
                        val currentExternalId = targetExternalIds.find(existing)
                        if (currentExternalId != null) {
                            throw ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Cohort $cohortId already has a $system target",
                            )
                        }
                        existing
                    }
                targetExternalIds.record(target, externalId)
                CohortTargetRow(target, externalId)
            }

        jobs.runAsync(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(linked.target.id!!), JobTrigger.SITE_ACTION)
        return linked
    }

    override fun create(
        cohortId: Long,
        system: TargetSystem,
        label: String,
        folderHint: String?,
    ): CohortTargetRow {
        // Validate before touching the provider so a duplicate/missing cohort
        // never creates an external target.
        writeTransaction.execute {
            requireCohort(cohortId)
            requireNoExistingMapping(cohortId, system)
        }

        val external = outsideTransaction.execute { strategies.require(system).create(label, folderHint) }

        return writeTransaction.execute {
            val target = targetRepo.save(newTarget(system, label, folder = folderHint, cohortId = cohortId))
            targetExternalIds.record(target, external.externalId)
            CohortTargetRow(target, external.externalId)
        }
    }

    override fun switchTarget(
        cohortId: Long,
        targetId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortTargetRow {
        val prep =
            writeTransaction.execute {
                val target = requireOwnedTarget(cohortId, targetId)
                TargetSystem.valueOf(target.system)
            }
        resolveTarget(prep, externalId)

        val switched =
            writeTransaction.execute {
                val target = requireOwnedTarget(cohortId, targetId)
                val system = TargetSystem.valueOf(target.system)
                val previousExternalId = targetExternalIds.find(target)
                targetExternalIds.record(target, externalId)
                Switched(target, system, previousExternalId)
            }

        if (deletePrevious && switched.previousExternalId != null && switched.previousExternalId != externalId) {
            jobs.runAsync(
                CohortJobs.DeleteExternalTarget,
                CohortJobs.DeleteExternalTargetPayload(switched.system.name, switched.previousExternalId),
                JobTrigger.SITE_ACTION,
            )
        }
        if (reconcileNow) {
            jobs.runAsync(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(targetId), JobTrigger.SITE_ACTION)
        }
        return CohortTargetRow(switched.target, externalId)
    }

    override fun materialize(targetId: Long): CohortTargetRef {
        // Re-check the id first: the job is deduped per target, but an external id may have
        // been recorded (admin link, an earlier run) since this job was enqueued.
        val prep =
            readOnlyTransaction.execute {
                val target =
                    targetRepo.findById(targetId).orElseThrow {
                        NonRetryableJobException("Target $targetId not found")
                    }
                MaterializePrep(TargetSystem.valueOf(target.system), target.label, target.folder, targetExternalIds.find(target))
            }
        prep.existingExternalId?.let { return CohortTargetRef(targetId, it) }

        throw NonRetryableJobException(
            "Target $targetId has no ${prep.system} id; materialize-target no longer creates targets. " +
                "Create or link an external target manually.",
        )
    }

    override fun deleteTarget(
        system: TargetSystem,
        externalTargetId: String,
    ) {
        val external = ExternalTarget(system, externalTargetId, strategies.descriptor(system).kind, externalTargetId)
        outsideTransaction.executeWithoutResult { strategies.require(system).delete(external) }
    }

    private fun requireCohort(cohortId: Long) =
        cohortRepo.findById(cohortId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
        }

    private fun requireNoExistingMapping(
        cohortId: Long,
        system: TargetSystem,
    ) {
        if (targetRepo.findByCohortIdAndSystem(cohortId, system.name) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cohort $cohortId already has a $system target",
            )
        }
    }

    private fun newTarget(
        system: TargetSystem,
        label: String,
        folder: String?,
        cohortId: Long,
    ) = Target(
        system = system.name,
        kind = strategies.descriptor(system).kind,
        label = label,
        folder = folder,
        cohortId = cohortId,
    )

    private fun requireOwnedTarget(
        cohortId: Long,
        targetId: Long,
    ): Target {
        val target =
            targetRepo.findById(targetId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Target $targetId not found")
            }
        if (target.cohortId != cohortId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Target $targetId is not a target of cohort $cohortId")
        }
        return target
    }

    private fun resolveTarget(
        system: TargetSystem,
        externalId: String,
    ) {
        outsideTransaction.execute { strategies.require(system).resolve(externalId) }
    }

    private data class Switched(
        val target: Target,
        val system: TargetSystem,
        val previousExternalId: String?,
    )

    private data class MaterializePrep(
        val system: TargetSystem,
        val label: String,
        val folder: String?,
        val existingExternalId: String?,
    )
}
