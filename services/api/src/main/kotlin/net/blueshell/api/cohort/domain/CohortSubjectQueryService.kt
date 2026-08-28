package net.blueshell.api.cohort.domain

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.api.ExternalIdMappingService.Companion.USER_AGGREGATE
import net.blueshell.api.shared.enums.TargetSystem
import java.time.Instant
import java.time.ZoneOffset
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Read-side service for the new admin Subjects dashboard. Mirrors the
 * existing [CohortQueryService] but operates on [CohortSubject]s — the
 * logical entities the engine syncs — and exposes the per-system
 * [Cohort] rows as nested "mappings" on each subject.
 */
@Service
class CohortSubjectQueryService(
    private val subjects: CohortSubjectRepository,
    private val cohorts: CohortRepository,
    private val cohortMembers: CohortMemberRepository,
    private val users: UserService,
    private val targetIds: CohortTargetIds,
    private val externalIds: ExternalIdMappingService,
    private val definitions: CohortDefinitionRegistry,
    private val strategies: TargetStrategies,
) {
    @Transactional(readOnly = true)
    fun summaries(): List<CohortSubjectSummary> {
        val allSubjects = subjects.findAll()
        if (allSubjects.isEmpty()) return emptyList()

        // Batch-loading counts + cohort labels would be nicer; for ~50
        // subjects the per-row queries are still cheap and readable.
        return allSubjects.map { subject ->
            val subjectId = subject.id!!
            CohortSubjectSummary(
                subject = subject,
                memberCount = cohortMembers.countBySubjectIdAndUserIdIsNotNull(subjectId).toInt(),
                mappingCount = cohorts.countBySubjectId(subjectId).toInt(),
            )
        }.sortedWith(
            compareBy({ it.subject.type.category() }, { it.subject.type.name }, { it.subject.label.lowercase() }),
        )
    }

    /**
     * The system a stored name stands for, or null when this build has no such system. A row
     * written by an older build outlives the constant it was written from, and reading one is
     * not an error worth an exception.
     */
    private fun targetSystemOrNull(system: String): TargetSystem? =
        TargetSystem.entries.firstOrNull { it.name == system }

    /**
     * External id to the account behind it, for the rows that have no account of their own.
     * Grouped by system because an external id only means anything within one.
     */
    private fun resolveStrangerOwners(
        members: List<CohortMember>,
        systemByCohortId: Map<Long, TargetSystem>,
    ): Map<String, Long> {
        val byExternalId = mutableMapOf<String, Long>()
        members
            .filter { it.userId == null }
            .mapNotNull { row ->
                val system = systemByCohortId[row.cohort.id] ?: return@mapNotNull null
                row.externalUserId?.let { system to it }
            }
            .groupBy({ it.first }, { it.second })
            .forEach { (system, externalUserIds) ->
                externalIds.findByExternalIds(USER_AGGREGATE, system.name, externalUserIds.toSet())
                    // A mapping row without an external id maps nothing; skip rather than
                    // keying the map on null.
                    .forEach { mapping -> mapping.externalId?.let { byExternalId[it] = mapping.aggregateId } }
            }
        return byExternalId
    }

    @Transactional(readOnly = true)
    fun detail(subjectId: Long): CohortSubjectDetail {
        val subject = subjects.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }
        val mappings = cohorts.findAllBySubjectId(subjectId).mapNotNull { cohort ->
            // A cohort can outlive the system it points at. Nothing on its row would work
            // without that system — reconciling, switching and importing all need its
            // strategy — so the row is left out and said out loud rather than taking the
            // whole page down with it.
            val system = targetSystemOrNull(cohort.system)
            if (system == null) {
                log.warn(
                    "[cohort] subject={} cohort={} points at '{}', which is not a system this build knows",
                    subjectId,
                    cohort.id,
                    cohort.system,
                )
                return@mapNotNull null
            }
            CohortMappingRow(
                cohort = cohort,
                externalId = targetIds.find(cohort),
                // The newest confirmation across this cohort's rows is when it was last seen
                // to agree with the external system.
                lastReconciledAt = cohortMembers.findAllByCohortId(cohort.id!!)
                    .mapNotNull { it.verifiedAt }
                    .maxOrNull()
                    ?.toInstant(ZoneOffset.UTC),
                // Where the target sits, from what was recorded when it was linked or moved.
                // Read from the row rather than from the system, so drawing this page costs
                // no call to Brevo.
                path = listOfNotNull(
                    runCatching { strategies.descriptor(system).systemLabel }
                        .getOrDefault(cohort.system),
                    cohort.folder?.takeIf { it.isNotBlank() },
                ),
            )
        }.sortedBy { it.cohort.system }

        // Every ledger row, not only the ones with a user. A row present externally and not
        // desired locally has no userId by definition, and it is exactly the row somebody
        // opens this page to find.
        val members = cohortMembers.findAllBySubjectId(subjectId)
        // Every row here came through the filter above, so every system named is one that
        // exists.
        val systemByCohortId = mappings.mapNotNull { row ->
            targetSystemOrNull(row.cohort.system)?.let { row.cohort.id!! to it }
        }.toMap()

        // Those rows carry an external id and nothing else. The mapping table knows which
        // account that id belongs to, if any, which is what turns it into a name.
        val ownerByExternalId = resolveStrangerOwners(members, systemByCohortId)

        val userIds = (members.mapNotNull { it.userId } + ownerByExternalId.values).distinct()
        val userById = users.findAllByIds(userIds).associateBy { it.id }
        val softDeletedIds = userIds
            .filter { userById[it] == null }
            .filter { users.isSoftDeleted(it) }
            .toSet()

        return CohortSubjectDetail(
            subject = subject,
            mappings = mappings,
            members = members.map { member ->
                val ownerId = member.userId ?: member.externalUserId?.let { ownerByExternalId[it] }
                CohortMemberRow(
                    member = member,
                    user = ownerId?.let { userById[it] },
                    isUserDeleted = ownerId != null && userById[ownerId] == null && softDeletedIds.contains(ownerId),
                    system = systemByCohortId[member.cohort.id],
                    state = member.state,
                    resolvedUserId = if (member.userId == null) ownerId else null,
                )
            }.sortedWith(
                compareBy(
                    { it.isUserDeleted },
                    { it.user?.fullName?.lowercase() ?: "~~~" },
                ),
            ),
            definitionKey = subject.definitionKey,
            // Derived rather than stored: a definition appearing or disappearing is a code
            // change, and a column recording it would be one deploy behind the truth.
            orphaned = subject.definitionKey?.let { definitions.byKey(it) } == null,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CohortSubjectQueryService::class.java)
    }
}

/** Read-model projection for the dashboard's top-level list. */
data class CohortSubjectSummary(
    val subject: CohortSubject,
    val memberCount: Int,
    val mappingCount: Int,
) {
    val category: CohortSubjectCategory get() = subject.type.category()
}

/** Detail view: subject + its per-system mappings + the rule it carries + members. */
data class CohortSubjectDetail(
    val subject: CohortSubject,
    val mappings: List<CohortMappingRow>,
    val members: List<CohortMemberRow>,
    /** Which definition in code produces this cohort; null once nothing does. */
    val definitionKey: String?,
    /** True when no definition produces this cohort any more — a disbanded committee, say. */
    val orphaned: Boolean,
)

/** One per-system mapping under a subject, with its external id resolved. */
data class CohortMappingRow(
    val cohort: Cohort,
    val externalId: String?,
    /** Newest confirmation across the cohort's rows; null when it has never been confirmed. */
    val lastReconciledAt: Instant? = null,
    /** The target's place on its system, outside in: the system, then any folder holding it. */
    val path: List<String> = emptyList(),
)
