package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetRepository
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

    // Suspends any active transaction (the one AbstractJsonJobHandler opens around a job) so
    // provider HTTP calls hold no DB connection (ADR-006, ADR-023).
    private val outsideTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    // Commits apart from the job's own transaction: a run that fails after the provider made the
    // target rolls that transaction back, and the claim has to be there for the retry to find.
    private val claimTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    override fun linkExisting(
        cohortId: Long,
        system: TargetSystem,
        externalId: String,
    ): CohortTargetRow {
        val listed = idAsListed(system, externalId)
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
                targetExternalIds.record(target, listed)
                CohortTargetRow(target, listed)
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
        // Validate before touching the provider so a linked or missing cohort never creates an
        // external target. A registered cohort's row without an id is the one to fill.
        val (cohort, unlinked) =
            writeTransaction.execute {
                requireCohort(cohortId) to unlinkedOrNone(cohortId, system)
            }
        val folder = folderHint ?: CohortFolders.forType(cohort.type)

        val external = outsideTransaction.execute { strategies.require(system).create(label, folder) }

        return writeTransaction.execute {
            val target =
                unlinked?.id?.let { id -> targetRepo.findById(id).orElseThrow().also { it.folder = folder } }
                    ?: targetRepo.save(newTarget(system, label, folder = folder, cohortId = cohortId))
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
        val listed = idAsListed(prep, externalId)

        val switched =
            writeTransaction.execute {
                val target = requireOwnedTarget(cohortId, targetId)
                val system = TargetSystem.valueOf(target.system)
                val previousExternalId = targetExternalIds.find(target)
                targetExternalIds.record(target, listed)
                Switched(target, system, previousExternalId)
            }

        if (deletePrevious && switched.previousExternalId != null && switched.previousExternalId != listed) {
            jobs.runAsync(
                CohortJobs.DeleteExternalTarget,
                CohortJobs.DeleteExternalTargetPayload(switched.system.name, switched.previousExternalId),
                JobTrigger.SITE_ACTION,
            )
        }
        if (reconcileNow) {
            jobs.runAsync(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(targetId), JobTrigger.SITE_ACTION)
        }
        return CohortTargetRow(switched.target, listed)
    }

    override fun createFor(targetId: Long): CohortTargetRef {
        val claim =
            claimTransaction.execute {
                val target =
                    targetRepo.findById(targetId).orElseThrow {
                        NonRetryableJobException("Target $targetId not found")
                    }
                val linked = targetExternalIds.find(target)
                val retry = target.targetClaimedAt != null
                if (linked == null && !retry) {
                    target.targetClaimedAt = Instant.now()
                    // A row registered before folders went by cohort type still names another.
                    target.folder = folderOf(target)
                    targetRepo.save(target)
                }
                Claim(TargetSystem.valueOf(target.system), target.label, target.folder, retry, linked)
            }
        claim.linked?.let { return CohortTargetRef(targetId, it) }

        val strategy = strategies.require(claim.system)
        val externalId =
            outsideTransaction.execute {
                // An earlier run may have made the target and failed before recording it.
                val made =
                    if (claim.retry) {
                        strategy.catalog(claim.label).firstOrNull { it.label == claim.label && claim.filedWith(it) }
                    } else {
                        null
                    }
                (made ?: strategy.create(claim.label, claim.folder)).externalId
            }

        writeTransaction.executeWithoutResult {
            targetExternalIds.record(targetRepo.findById(targetId).orElseThrow(), externalId)
        }
        // Pushes that failed while the cohort had no target are made good by the reconcile.
        jobs.runAsync(CohortJobs.ReconcileList, CohortJobs.ReconcileListPayload(targetId), JobTrigger.ANOTHER_JOB)
        return CohortTargetRef(targetId, externalId)
    }

    override fun createMissing(): Int {
        val missing = readOnlyTransaction.execute { targetRepo.findAllByCohortIdIsNotNullAndExternalIdIsNull().mapNotNull { it.id } }
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
        val external = ExternalTarget(system, externalTargetId, strategies.descriptor(system).kind, externalTargetId)
        outsideTransaction.executeWithoutResult { strategies.require(system).delete(external) }
    }

    private fun folderOf(target: Target): String? =
        target.cohortId
            ?.let { cohortRepo.findById(it).orElse(null) }
            ?.let { CohortFolders.forType(it.type) }
            ?: target.folder

    private fun requireCohort(cohortId: Long) =
        cohortRepo.findById(cohortId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
        }

    /** The cohort's target without an id yet, or none; a linked one is refused, to be switched instead. */
    private fun unlinkedOrNone(
        cohortId: Long,
        system: TargetSystem,
    ): Target? {
        val existing = targetRepo.findByCohortIdAndSystem(cohortId, system.name) ?: return null
        if (targetExternalIds.find(existing) != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cohort $cohortId already has a $system target; switch it instead",
            )
        }
        return existing
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

    // The id as the system writes it: one typed another way would pass for a different target.
    private fun idAsListed(
        system: TargetSystem,
        externalId: String,
    ): String = outsideTransaction.execute { strategies.require(system).resolve(externalId) }?.externalId ?: externalId

    private data class Switched(
        val target: Target,
        val system: TargetSystem,
        val previousExternalId: String?,
    )

    private data class Claim(
        val system: TargetSystem,
        val label: String,
        val folder: String?,
        val retry: Boolean,
        val linked: String?,
    ) {
        /** Systems match a folder's name whatever its case; with no folder the name alone decides. */
        fun filedWith(external: ExternalTarget): Boolean = folder == null || external.folderLabel.equals(folder, ignoreCase = true)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortTargetingService::class.java)
    }
}
