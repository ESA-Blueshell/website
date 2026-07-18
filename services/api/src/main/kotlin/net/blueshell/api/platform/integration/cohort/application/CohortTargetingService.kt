package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargetRef
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.CohortJobs
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

/**
 * Application implementation of [CohortTargeting]. External target creation
 * runs outside any DB transaction; the local `Cohort` row and its external id
 * are persisted in a short write transaction afterwards so a provider failure
 * leaves no half-written row. [CohortTargetIds] owns every write of the id.
 */
@Service
class CohortTargetingService(
    private val cohortRepo: CohortRepository,
    private val subjectRepo: CohortSubjectRepository,
    private val targetIds: CohortTargetIds,
    private val strategies: TargetStrategies,
    private val jobs: TrackedJobDispatcher,
    transactionManager: PlatformTransactionManager,
) : CohortTargeting {

    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val writeTransaction = TransactionTemplate(transactionManager)

    // Suspends any active transaction (e.g. the one AbstractJsonJobHandler opens
    // around deleteTarget / materialize) so provider HTTP calls hold no DB
    // connection — ADR-006/ADR-023.
    private val outsideTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
    }

    override fun linkExisting(subjectId: Long, system: TargetSystem, externalId: String): CohortMappingRow {
        resolveTarget(system, externalId)
        val linked = writeTransaction.execute {
            val subject = requireSubject(subjectId)
            val existing = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
            val cohort = if (existing == null) {
                cohortRepo.save(newCohort(system, subject.label, folder = null, subjectId = subjectId))
            } else {
                val currentExternalId = targetIds.find(existing)
                if (currentExternalId != null) {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Subject $subjectId already has a $system target",
                    )
                }
                existing
            }
            targetIds.record(cohort, externalId)
            CohortMappingRow(cohort, externalId)
        }

        jobs.enqueue(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(linked.cohort.id!!))
        return linked
    }

    override fun create(subjectId: Long, system: TargetSystem, label: String, folderHint: String?): CohortMappingRow {
        // Validate before touching the provider so a duplicate/missing subject
        // never creates an external target.
        writeTransaction.execute {
            requireSubject(subjectId)
            requireNoExistingMapping(subjectId, system)
        }

        val target = outsideTransaction.execute { strategies.require(system).create(label, folderHint) }!!

        return writeTransaction.execute {
            val cohort = cohortRepo.save(newCohort(system, label, folder = folderHint, subjectId = subjectId))
            targetIds.record(cohort, target.externalId)
            CohortMappingRow(cohort, target.externalId)
        }!!
    }

    override fun switchTarget(
        subjectId: Long,
        cohortId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortMappingRow {
        val prep = writeTransaction.execute {
            val cohort = requireOwnedCohort(subjectId, cohortId)
            TargetSystem.valueOf(cohort.system)
        }!!
        resolveTarget(prep, externalId)

        val switched = writeTransaction.execute {
            val cohort = requireOwnedCohort(subjectId, cohortId)
            val system = TargetSystem.valueOf(cohort.system)
            val previousExternalId = targetIds.find(cohort)
            targetIds.record(cohort, externalId)
            Switched(cohort, system, previousExternalId)
        }!!

        if (deletePrevious && switched.previousExternalId != null && switched.previousExternalId != externalId) {
            jobs.enqueue(
                CohortJobs.DeleteExternalTarget,
                CohortJobs.DeleteExternalTargetPayload(switched.system.name, switched.previousExternalId),
            )
        }
        if (reconcileNow) {
            jobs.enqueue(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(cohortId))
        }
        return CohortMappingRow(switched.cohort, externalId)
    }

    override fun materialize(cohortId: Long): CohortTargetRef {
        // Re-check the id first: the job is cohort-deduped, but a target may have
        // been recorded (admin link, an earlier run) since this job was enqueued.
        val prep = readOnlyTransaction.execute {
            val cohort = cohortRepo.findById(cohortId).orElseThrow {
                NonRetryableJobException("Cohort $cohortId not found")
            }
            MaterializePrep(TargetSystem.valueOf(cohort.system), cohort.label, cohort.folder, targetIds.find(cohort))
        }!!
        prep.existingExternalId?.let { return CohortTargetRef(cohortId, it) }

        throw NonRetryableJobException(
            "Cohort $cohortId has no ${prep.system} target; materialize-target no longer creates targets. " +
                "Create or link an external target manually.",
        )
    }

    override fun deleteTarget(system: TargetSystem, externalTargetId: String) {
        val target = ExternalTarget(system, externalTargetId, strategies.descriptor(system).kind, externalTargetId)
        outsideTransaction.executeWithoutResult { strategies.require(system).delete(target) }
    }

    private fun requireSubject(subjectId: Long) =
        subjectRepo.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }

    private fun requireNoExistingMapping(subjectId: Long, system: TargetSystem) {
        if (cohortRepo.findBySubjectIdAndSystem(subjectId, system.name) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Subject $subjectId already has a $system target",
            )
        }
    }

    private fun newCohort(system: TargetSystem, label: String, folder: String?, subjectId: Long) =
        Cohort(
            system = system.name,
            kind = strategies.descriptor(system).kind,
            label = label,
            folder = folder,
            subjectId = subjectId,
        )

    private fun requireOwnedCohort(subjectId: Long, cohortId: Long): Cohort {
        val cohort = cohortRepo.findById(cohortId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
        }
        if (cohort.subjectId != subjectId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId is not a target of subject $subjectId")
        }
        return cohort
    }

    private fun resolveTarget(system: TargetSystem, externalId: String) {
        outsideTransaction.execute { strategies.require(system).resolve(externalId) }
    }

    private data class Switched(val cohort: Cohort, val system: TargetSystem, val previousExternalId: String?)

    private data class MaterializePrep(
        val system: TargetSystem,
        val label: String,
        val folder: String?,
        val existingExternalId: String?,
    )
}
