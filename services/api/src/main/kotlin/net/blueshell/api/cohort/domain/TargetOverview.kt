package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Service
import java.time.Instant

/** One list on a system as its platform page shows it: where it is, the cohort it follows and its drift. */
data class ListedTarget(
    val externalId: String,
    val label: String,
    val folderLabel: String?,
    /** How many contacts the system says the list holds. */
    val memberCount: Long?,
    val targetId: Long?,
    val cohortId: Long?,
    val cohortLabel: String?,
    val cohortType: CohortType?,
    /** From the newest reconcile: people who should be on the list and are not. */
    val missing: Int?,
    /** From the newest reconcile: people on the list who should not be. */
    val extra: Int?,
    /** From the newest reconcile: people who should be on it and have no account there to put on it. */
    val unreachable: Int?,
    val lastReconciledAt: Instant?,
    val enforced: Boolean,
)

/** A cohort whose list the site expects on the system and that does not exist yet. */
data class MissingTarget(
    val targetId: Long,
    val cohortId: Long,
    val cohortLabel: String,
    val cohortType: CohortType,
    /** The folder the list is created in. */
    val folder: String?,
    /** How many of our people the list will hold. */
    val memberCount: Int,
    /** Whether a create has been set off and not finished. */
    val creating: Boolean,
)

data class TargetOverviewResult(
    val lists: List<ListedTarget>,
    val missing: List<MissingTarget>,
    val lastReconciledAt: Instant?,
)

/** Every list in a system's account, linked to a cohort or not, and the lists the site expects that are missing. */
@Service
class TargetOverview(
    private val catalog: TargetCatalog,
    private val targets: TargetRepository,
    private val cohorts: CohortRepository,
    private val targetMembers: TargetMemberRepository,
    private val runs: TargetReconcileRunRepository,
    private val jobs: JobQueue,
) {
    fun of(system: TargetSystem): TargetOverviewResult {
        val external = catalog.search(system, null)
        val ours = targets.findAllBySystem(system.name)
        val cohortById = cohorts.findAllById(ours.mapNotNull { it.cohortId }.toSet()).associateBy { requireNotNull(it.id) }
        val byExternalId = ours.filter { !it.externalId.isNullOrBlank() }.associateBy { it.externalId }
        val lists =
            external.map { list ->
                val target = byExternalId[list.externalId]
                rowOf(list, target, target?.cohortId?.let(cohortById::get))
            }
        val missing =
            missingOf(system).mapNotNull { target ->
                val cohort = target.cohortId?.let(cohortById::get) ?: return@mapNotNull null
                MissingTarget(
                    targetId = requireNotNull(target.id),
                    cohortId = requireNotNull(cohort.id),
                    cohortLabel = cohort.label,
                    cohortType = cohort.type,
                    folder = target.folder,
                    memberCount = targetMembers.countByTargetIdAndUserIdIsNotNull(requireNotNull(target.id)).toInt(),
                    creating = target.targetClaimedAt != null,
                )
            }
        return TargetOverviewResult(lists, missing, lists.mapNotNull { it.lastReconciledAt }.maxOrNull())
    }

    /** One list on the system, with the cohort it follows and its newest drift. */
    fun one(
        system: TargetSystem,
        externalId: String,
    ): ListedTarget {
        val list = catalog.find(system, externalId)
        val target = targets.findFirstBySystemAndExternalId(system.name, externalId)
        val cohort = target?.cohortId?.let { cohorts.findById(it).orElse(null) }
        return rowOf(list, target, cohort)
    }

    private fun rowOf(
        list: ExternalTarget,
        target: Target?,
        cohort: Cohort?,
    ): ListedTarget {
        val newest = target?.id?.let(runs::findFirstByTargetIdOrderByStartedAtDesc)
        return ListedTarget(
            externalId = list.externalId,
            label = list.label,
            folderLabel = list.folderLabel,
            memberCount = list.memberCount,
            targetId = target?.id,
            cohortId = cohort?.id,
            cohortLabel = cohort?.label,
            cohortType = cohort?.type,
            missing = newest?.oursOnly,
            extra = newest?.theirsOnly,
            unreachable = newest?.unreachable,
            lastReconciledAt = newest?.startedAt,
            enforced = target?.enforced ?: false,
        )
    }

    /**
     * Queues the create-target job for each of [targetIds] that is a missing list on [system], or for
     * every missing one when none are named, and answers how many were queued.
     */
    fun createMissing(
        system: TargetSystem,
        targetIds: Collection<Long>,
    ): Int {
        val missing = missingOf(system).mapNotNull { it.id }.filter { targetIds.isEmpty() || it in targetIds }
        missing.forEach { jobs.runAsync(CohortJobs.CreateCohortTarget, CohortJobs.CreateCohortTargetPayload(it), JobTrigger.SITE_ACTION) }
        return missing.size
    }

    private fun missingOf(system: TargetSystem): List<Target> =
        targets.findAllByCohortIdIsNotNullAndExternalIdIsNull().filter { it.system == system.name }
}
