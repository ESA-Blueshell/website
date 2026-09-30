package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.ZoneOffset

/** What the cohort pages read: the cohorts, each with the targets that mirror it and its ledger rows. */
@Service
class CohortQueryService(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetMembers: TargetMemberRepository,
    private val users: UserService,
    private val targetExternalIds: CohortTargetIds,
    private val externalIds: ExternalIdMappingService,
    private val definitions: CohortDefinitionRegistry,
    private val strategies: TargetStrategies,
    private val runs: TargetReconcileRunRepository,
    private val resolutions: DriftResolutionRepository,
) {
    @Transactional(readOnly = true)
    fun summaries(): List<CohortSummary> {
        val allCohorts = cohorts.findAll()
        if (allCohorts.isEmpty()) return emptyList()

        // Batch-loading counts + cohort labels would be nicer; for ~50
        // cohorts the per-row queries are still cheap and readable.
        return allCohorts
            .map { cohort ->
                val cohortId = cohort.id!!
                CohortSummary(
                    cohort = cohort,
                    memberCount = targetMembers.countByCohortIdAndUserIdIsNotNull(cohortId).toInt(),
                    mappingCount = targets.countByCohortId(cohortId).toInt(),
                )
            }.sortedWith(
                compareBy({ it.cohort.type.category() }, { it.cohort.type.name }, { it.cohort.label.lowercase() }),
            )
    }

    /**
     * The system a stored name stands for, or null when this build has no such system. A row
     * written by an older build outlives the constant it was written from, and reading one is
     * not an error worth an exception.
     */
    private fun targetSystemOrNull(system: String): TargetSystem? = TargetSystem.entries.firstOrNull { it.name == system }

    /**
     * External id to the account behind it, for the rows that have no account of their own.
     * Grouped by system because an external id only means anything within one.
     */
    private fun resolveStrangerOwners(
        members: List<TargetMember>,
        systemByTargetId: Map<Long, TargetSystem>,
    ): Map<String, Long> {
        val byExternalId = mutableMapOf<String, Long>()
        members
            .filter { it.userId == null }
            .mapNotNull { row ->
                val system = systemByTargetId[row.target.id] ?: return@mapNotNull null
                row.externalUserId?.let { system to it }
            }.groupBy({ it.first }, { it.second })
            .forEach { (system, externalUserIds) ->
                externalIds
                    .findByExternalIds(USER_AGGREGATE, system.name, externalUserIds.toSet())
                    // A mapping row without an external id maps nothing; skip rather than
                    // keying the map on null.
                    .forEach { mapping -> mapping.externalId?.let { byExternalId[it] = mapping.aggregateId } }
            }
        return byExternalId
    }

    /** Every target with how many of our people it holds, for the pickers. */
    @Transactional(readOnly = true)
    fun targets(): List<TargetSummary> =
        targets.findAll().map { target ->
            TargetSummary(
                target = target,
                memberCount = targetMembers.countByTargetIdAndUserIdIsNotNull(target.id!!).toInt(),
            )
        }

    @Transactional(readOnly = true)
    // Assembles one view out of six repositories; each block reads one of them.
    @Suppress("LongMethod")
    fun detail(cohortId: Long): CohortDetail {
        val cohort =
            cohorts.findById(cohortId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
            }
        val mappings =
            targets
                .findAllByCohortId(cohortId)
                .mapNotNull { target ->
                    // A cohort can outlive the system it points at. Nothing on its row would work
                    // without that system — reconciling, switching and importing all need its
                    // strategy — so the row is left out and said out loud rather than taking the
                    // whole page down with it.
                    val system = targetSystemOrNull(target.system)
                    if (system == null) {
                        log.warn(
                            "[cohort] cohort={} target={} points at '{}', which is not a system this build knows",
                            cohortId,
                            target.id,
                            target.system,
                        )
                        return@mapNotNull null
                    }
                    CohortTargetRow(
                        target = target,
                        externalId = targetExternalIds.find(target),
                        // The newest confirmation across this cohort's rows is when it was last seen
                        // to agree with the external system.
                        lastReconciledAt =
                            targetMembers
                                .findAllByTargetId(target.id!!)
                                .mapNotNull { it.verifiedAt }
                                .maxOrNull()
                                ?.toInstant(ZoneOffset.UTC),
                        // The system only: the folder is read from the system by the caller, outside
                        // this transaction, since a list moved in Brevo is somewhere its row cannot say.
                        path = listOf(runCatching { strategies.descriptor(system).system.shownName }.getOrDefault(target.system)),
                        runs = runs.findTop10ByTargetIdOrderByStartedAtDesc(target.id!!),
                    )
                }.sortedBy { it.target.system }

        // Every ledger row, not only the ones with a user. A row present externally and not
        // desired locally has no userId by definition, and it is exactly the row somebody
        // opens this page to find.
        val members = targetMembers.findAllByCohortId(cohortId)
        // Every row here came through the filter above, so every system named is one that
        // exists.
        val systemByTargetId =
            mappings
                .mapNotNull { row ->
                    targetSystemOrNull(row.target.system)?.let { row.target.id!! to it }
                }.toMap()

        // Those rows carry an external id and nothing else. The mapping table knows which
        // account that id belongs to, if any, which is what turns it into a name.
        val ownerByExternalId = resolveStrangerOwners(members, systemByTargetId)

        val recentResolutions = resolutions.findTop20ByTargetIdInOrderByResolvedAtDesc(systemByTargetId.keys)
        val userIds =
            (
                members.mapNotNull { it.userId } + ownerByExternalId.values +
                    recentResolutions.flatMap { listOfNotNull(it.userId, it.resolvedBy) }
            ).distinct()
        val userById = users.findAllByIds(userIds).associateBy { it.id }
        val softDeletedIds =
            userIds
                .filter { userById[it] == null }
                .filter { users.isSoftDeleted(it) }
                .toSet()

        return CohortDetail(
            cohort = cohort,
            mappings = mappings,
            members =
                members
                    .map { member ->
                        val ownerId = member.userId ?: member.externalUserId?.let { ownerByExternalId[it] }
                        TargetMemberRow(
                            member = member,
                            user = ownerId?.let { userById[it] },
                            isUserDeleted = ownerId != null && userById[ownerId] == null && softDeletedIds.contains(ownerId),
                            system = systemByTargetId[member.target.id],
                            state = member.state,
                            resolvedUserId = if (member.userId == null) ownerId else null,
                        )
                    }.sortedWith(
                        compareBy(
                            { it.isUserDeleted },
                            { it.user?.fullName?.lowercase() ?: "~~~" },
                        ),
                    ),
            definitionKey = cohort.definitionKey,
            // Derived rather than stored: a definition appearing or disappearing is a code
            // change, and a column recording it would be one deploy behind the truth.
            orphaned = cohort.definitionKey?.let { definitions.byKey(it) } == null,
            resolutions =
                recentResolutions.map { resolution ->
                    DriftResolutionRow(
                        resolution = resolution,
                        system = systemByTargetId.getValue(resolution.targetId),
                        personName = resolution.userId?.let { userById[it]?.fullName } ?: resolution.label,
                        resolvedByName = resolution.resolvedBy?.let { userById[it]?.fullName },
                    )
                },
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortQueryService::class.java)
    }
}

/** A target and how many of our people it holds. */
data class TargetSummary(
    val target: Target,
    val memberCount: Int,
)

/** Read-model projection for the dashboard's top-level list. */
data class CohortSummary(
    val cohort: Cohort,
    val memberCount: Int,
    val mappingCount: Int,
) {
    val category: CohortCategory get() = cohort.type.category()
}

/** Detail view: cohort + its per-system mappings + the rule it carries + members. */
data class CohortDetail(
    val cohort: Cohort,
    val mappings: List<CohortTargetRow>,
    val members: List<TargetMemberRow>,
    /** Which definition in code produces this cohort; null once nothing does. */
    val definitionKey: String?,
    /** True when no definition produces this cohort any more — a disbanded committee, say. */
    val orphaned: Boolean,
    /** The latest drift resolutions across the cohort's targets, newest first. */
    val resolutions: List<DriftResolutionRow> = emptyList(),
)

/** One recorded resolution, with the names of the person it concerned and of who resolved it. */
data class DriftResolutionRow(
    val resolution: DriftResolution,
    val system: TargetSystem,
    val personName: String?,
    /** Null when the api resolved it on its own behalf, or the account is gone. */
    val resolvedByName: String?,
)

/** One per-system mapping under a cohort, with its external id resolved. */
data class CohortTargetRow(
    val target: Target,
    val externalId: String?,
    /** Newest confirmation across the cohort's rows; null when it has never been confirmed. */
    val lastReconciledAt: Instant? = null,
    /** The target's place on its system, outside in: the system, then any folder holding it. */
    val path: List<String> = emptyList(),
    /** The target's recent reconciles, newest first; the first is its current drift. */
    val runs: List<TargetReconcileRun> = emptyList(),
)

/**
 * One ledger row on a cohort's page, with the joined user record
 * if the user is still active. [isUserDeleted] is true when the user
 * has been soft-deleted but the cohort_member row was retained for
 * historical stats — the admin UI renders these in a muted style with
 * a "Deleted" badge instead of the active user details.
 */
data class TargetMemberRow(
    val member: TargetMember,
    val user: User?,
    val isUserDeleted: Boolean = false,
    /**
     * Which system's ledger this row belongs to. A row is per (cohort, user), and a cohort is
     * per system, so a cohort with two targets holds two rows for the same person.
     */
    val system: TargetSystem? = null,
    /**
     * The state the row is in. Defaulted so the older per-cohort projection, which does not
     * report it, is unaffected.
     */
    val state: TargetMemberState? = null,
    /**
     * For a row present externally but not desired locally: the account behind that external
     * id, once resolved. Null when nothing local matches it.
     */
    val resolvedUserId: Long? = null,
)
