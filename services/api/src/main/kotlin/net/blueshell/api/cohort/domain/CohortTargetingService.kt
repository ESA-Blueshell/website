package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * External target creation
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
        subjectId: Long,
        system: TargetSystem,
        externalId: String,
    ): CohortMappingRow {
        resolveTarget(system, externalId)
        val linked =
            writeTransaction.execute {
                val subject = requireSubject(subjectId)
                val existing = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name)
                val cohort =
                    if (existing == null) {
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

        jobs.reconcileTarget(linked.cohort.id!!, JobTrigger.SITE_ACTION)
        return linked
    }

    override fun create(
        subjectId: Long,
        system: TargetSystem,
        label: String,
        folderHint: String?,
    ): CohortMappingRow {
        // Validate before touching the provider so a linked or missing subject never creates an
        // external target. A registered cohort's row without an id is the one to fill.
        val unlinked =
            writeTransaction.execute {
                requireSubject(subjectId)
                unlinkedOrNone(subjectId, system)
            }
        val folder = folderHint ?: unlinked?.folder

        val target = outsideTransaction.execute { strategies.require(system).create(label, folder) }

        return writeTransaction.execute {
            val cohort =
                unlinked?.id?.let { id -> cohortRepo.findById(id).orElseThrow().also { it.folder = folder } }
                    ?: cohortRepo.save(newCohort(system, label, folder = folder, subjectId = subjectId))
            targetIds.record(cohort, target.externalId)
            CohortMappingRow(cohort, target.externalId)
        }
    }

    override fun switchTarget(
        subjectId: Long,
        cohortId: Long,
        externalId: String,
        deletePrevious: Boolean,
        reconcileNow: Boolean,
    ): CohortMappingRow {
        val prep =
            writeTransaction.execute {
                val cohort = requireOwnedCohort(subjectId, cohortId)
                TargetSystem.valueOf(cohort.system)
            }
        resolveTarget(prep, externalId)

        val switched =
            writeTransaction.execute {
                val cohort = requireOwnedCohort(subjectId, cohortId)
                val system = TargetSystem.valueOf(cohort.system)
                val previousExternalId = targetIds.find(cohort)
                targetIds.record(cohort, externalId)
                Switched(cohort, system, previousExternalId)
            }

        if (deletePrevious && switched.previousExternalId != null && switched.previousExternalId != externalId) {
            jobs.runAsync(
                CohortJobs.DeleteExternalTarget,
                CohortJobs.DeleteExternalTargetPayload(switched.system.name, switched.previousExternalId),
                JobTrigger.SITE_ACTION,
            )
        }
        if (reconcileNow) {
            jobs.reconcileTarget(cohortId, JobTrigger.SITE_ACTION)
        }
        return CohortMappingRow(switched.cohort, externalId)
    }

    override fun createFor(cohortId: Long): CohortTargetRef {
        val claim =
            writeTransaction.execute {
                val cohort =
                    cohortRepo.findById(cohortId).orElseThrow {
                        NonRetryableJobException("Cohort $cohortId not found")
                    }
                val linked = targetIds.find(cohort)
                val retry = cohort.targetClaimedAt != null
                if (linked == null && !retry) {
                    cohort.targetClaimedAt = Instant.now()
                    cohortRepo.save(cohort)
                }
                Claim(TargetSystem.valueOf(cohort.system), cohort.label, cohort.folder, retry, linked)
            }
        claim.linked?.let { return CohortTargetRef(cohortId, it) }

        val strategy = strategies.require(claim.system)
        val externalId =
            outsideTransaction.execute {
                // An earlier run may have made the target and failed before recording it.
                val made =
                    if (claim.retry) {
                        strategy.catalog(claim.label).firstOrNull { it.label == claim.label && it.folderLabel == claim.folder }
                    } else {
                        null
                    }
                (made ?: strategy.create(claim.label, claim.folder)).externalId
            }

        writeTransaction.executeWithoutResult {
            targetIds.record(cohortRepo.findById(cohortId).orElseThrow(), externalId)
        }
        // Pushes that failed while the cohort had no target are made good by the reconcile.
        jobs.reconcileTarget(cohortId, JobTrigger.ANOTHER_JOB)
        return CohortTargetRef(cohortId, externalId)
    }

    override fun createMissing(): Int {
        val missing = readOnlyTransaction.execute { cohortRepo.findAllBySubjectIdIsNotNullAndExternalIdIsNull().mapNotNull { it.id } }
        missing.forEach {
            jobs.runAsync(CohortJobs.CreateCohortTarget, CohortJobs.CreateCohortTargetPayload(it), JobTrigger.ANOTHER_JOB)
        }
        log.info("[cohort] queued a create-target job for {} cohort(s) without a target", missing.size)
        return missing.size
    }

    override fun deleteTarget(
        system: TargetSystem,
        externalTargetId: String,
    ) {
        val target = ExternalTarget(system, externalTargetId, strategies.descriptor(system).kind, externalTargetId)
        outsideTransaction.executeWithoutResult { strategies.require(system).delete(target) }
    }

    private fun requireSubject(subjectId: Long) =
        subjectRepo.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }

    /** The subject's target without an id yet, or none; a linked one is refused, to be switched instead. */
    private fun unlinkedOrNone(
        subjectId: Long,
        system: TargetSystem,
    ): Cohort? {
        val existing = cohortRepo.findBySubjectIdAndSystem(subjectId, system.name) ?: return null
        if (targetIds.find(existing) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Subject $subjectId already has a $system target; switch it instead",
            )
        }
        return existing
    }

    private fun newCohort(
        system: TargetSystem,
        label: String,
        folder: String?,
        subjectId: Long,
    ) = Cohort(
        system = system.name,
        kind = strategies.descriptor(system).kind,
        label = label,
        folder = folder,
        subjectId = subjectId,
    )

    private fun requireOwnedCohort(
        subjectId: Long,
        cohortId: Long,
    ): Cohort {
        val cohort =
            cohortRepo.findById(cohortId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
            }
        if (cohort.subjectId != subjectId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId is not a target of subject $subjectId")
        }
        return cohort
    }

    private fun resolveTarget(
        system: TargetSystem,
        externalId: String,
    ) {
        outsideTransaction.execute { strategies.require(system).resolve(externalId) }
    }

    private data class Switched(
        val cohort: Cohort,
        val system: TargetSystem,
        val previousExternalId: String?,
    )

    private data class Claim(
        val system: TargetSystem,
        val label: String,
        val folder: String?,
        val retry: Boolean,
        val linked: String?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(CohortTargetingService::class.java)
    }
}
