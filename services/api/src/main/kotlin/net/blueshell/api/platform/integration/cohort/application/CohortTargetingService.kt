package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargeting
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortTargetRef
import net.blueshell.api.platform.integration.cohort.port.out.CohortPortRegistry
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
    private val registry: CohortPortRegistry,
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
        val port = registry.find(system)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$system has no cohort target kind")
        return writeTransaction.execute {
            val subject = requireSubject(subjectId)
            requireNoExistingMapping(subjectId, system)
            val cohort = cohortRepo.save(newCohort(system, port.kind, subject.label, folder = null, subjectId = subjectId))
            targetIds.record(cohort, externalId)
            CohortMappingRow(cohort, externalId)
        }!!
    }

    override fun create(subjectId: Long, system: TargetSystem, label: String, folderHint: String?): CohortMappingRow {
        // Validate before touching the provider so a duplicate/missing subject
        // never creates an external target.
        writeTransaction.execute {
            requireSubject(subjectId)
            requireNoExistingMapping(subjectId, system)
        }

        val port = registry.require(system)
        val externalId = outsideTransaction.execute { port.createCohort(label, folderHint) }!!

        return writeTransaction.execute {
            val cohort = cohortRepo.save(newCohort(system, port.kind, label, folder = folderHint, subjectId = subjectId))
            targetIds.record(cohort, externalId)
            CohortMappingRow(cohort, externalId)
        }!!
    }

    override fun switchTarget(
        subjectId: Long,
        cohortId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortMappingRow {
        val switched = writeTransaction.execute {
            val cohort = cohortRepo.findById(cohortId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
            }
            // The route carries the subject id; reject a cohort that is not its
            // target so a wrong-path admin call cannot repoint another subject's.
            if (cohort.subjectId != subjectId) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId is not a target of subject $subjectId")
            }
            val system = cohort.system
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
            MaterializePrep(cohort.system, cohort.label, cohort.folder, targetIds.find(cohort))
        }!!
        prep.existingExternalId?.let { return CohortTargetRef(cohortId, it) }

        // Pass the folder — the old lazy ADD path created the list with no
        // folder while admin creation passed it.
        val created = outsideTransaction.execute { registry.require(prep.system).createCohort(prep.label, prep.folder) }!!

        return writeTransaction.execute {
            val cohort = cohortRepo.findById(cohortId).orElseThrow {
                NonRetryableJobException("Cohort $cohortId not found")
            }
            targetIds.find(cohort)?.let { return@execute CohortTargetRef(cohortId, it) }
            try {
                targetIds.record(cohort, created)
            } catch (e: Exception) {
                // The remote target exists but could not be recorded. Fail
                // terminally carrying its id so an operator links it, rather
                // than letting retries create a second remote target.
                throw NonRetryableJobException(
                    "Created ${prep.system} target '$created' for cohort $cohortId but could not record it; link it manually",
                    e,
                )
            }
            CohortTargetRef(cohortId, created)
        }!!
    }

    override fun deleteTarget(system: TargetSystem, externalTargetId: String) {
        outsideTransaction.executeWithoutResult { registry.require(system).deleteCohort(externalTargetId) }
    }

    private fun requireSubject(subjectId: Long) =
        subjectRepo.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }

    private fun requireNoExistingMapping(subjectId: Long, system: TargetSystem) {
        if (cohortRepo.findBySubjectIdAndSystem(subjectId, system) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Subject $subjectId already has a $system target",
            )
        }
    }

    private fun newCohort(system: TargetSystem, kind: CohortKind, label: String, folder: String?, subjectId: Long) =
        Cohort(
            system = system,
            kind = kind,
            label = label,
            folder = folder,
            subjectId = subjectId,
        )

    private data class Switched(val cohort: Cohort, val system: TargetSystem, val previousExternalId: String?)

    private data class MaterializePrep(
        val system: TargetSystem,
        val label: String,
        val folder: String?,
        val existingExternalId: String?,
    )
}
