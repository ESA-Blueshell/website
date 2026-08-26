package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectCategory
import net.blueshell.api.platform.integration.cohort.persistence.state
import net.blueshell.api.shared.enums.TargetSystem
import java.time.Instant
import java.time.ZoneOffset
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
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

    @Transactional(readOnly = true)
    fun detail(subjectId: Long): CohortSubjectDetail {
        val subject = subjects.findById(subjectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Subject $subjectId not found")
        }
        val mappings = cohorts.findAllBySubjectId(subjectId).map { cohort ->
            CohortMappingRow(
                cohort = cohort,
                externalId = targetIds.find(cohort),
                // The newest confirmation across this cohort's rows is when it was last seen
                // to agree with the external system.
                lastReconciledAt = cohortMembers.findAllByCohortId(cohort.id!!)
                    .mapNotNull { it.verifiedAt }
                    .maxOrNull()
                    ?.toInstant(ZoneOffset.UTC),
            )
        }.sortedBy { it.cohort.system }

        val members = cohortMembers.findAllBySubjectIdAndUserIdIsNotNull(subjectId)
        val systemByCohortId = mappings.associate { it.cohort.id!! to TargetSystem.valueOf(it.cohort.system) }

        val userIds = members.mapNotNull { it.userId }.distinct()
        val userById = users.findAllByIds(userIds).associateBy { it.id }
        val softDeletedIds = userIds
            .filter { userById[it] == null }
            .filter { users.isSoftDeleted(it) }
            .toSet()

        return CohortSubjectDetail(
            subject = subject,
            mappings = mappings,
            members = members.map { member ->
                CohortMemberRow(
                    member = member,
                    user = userById[member.userId!!],
                    isUserDeleted = userById[member.userId!!] == null && softDeletedIds.contains(member.userId!!),
                    system = systemByCohortId[member.cohort.id],
                    state = member.state,
                )
            }.sortedWith(
                compareBy(
                    { it.isUserDeleted },
                    { it.user?.fullName?.lowercase() ?: "~~~" },
                ),
            ),
            rules = subject.ruleView()?.let { listOf(it) } ?: emptyList(),
        )
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
    val rules: List<CohortRuleView>,
)

/** One per-system mapping under a subject, with its external id resolved. */
data class CohortMappingRow(
    val cohort: Cohort,
    val externalId: String?,
    /** Newest confirmation across the cohort's rows; null when it has never been confirmed. */
    val lastReconciledAt: Instant? = null,
)
