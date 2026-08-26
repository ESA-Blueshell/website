package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.shared.enums.CohortMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Read-side application service: lists cohorts for the admin UI
 * pickers and inspection pages, and resolves the detail view that
 * shows members + rules. The controller depends on this (per the
 * platform-architecture rules controllers must not access
 * repositories directly).
 */
@Service
class CohortQueryService(
    private val cohorts: CohortRepository,
    private val cohortMembers: CohortMemberRepository,
    private val subjects: CohortSubjectRepository,
    private val users: UserService,
    private val targetIds: CohortTargetIds,
) {
    @Transactional(readOnly = true)
    fun summaries(): List<CohortSummary> =
        cohorts.findAll().map { cohort ->
            CohortSummary(
                cohort = cohort,
                memberCount = cohortMembers.countByCohortIdAndUserIdIsNotNull(cohort.id!!).toInt(),
                externalId = targetIds.find(cohort),
            )
        }

    @Transactional(readOnly = true)
    fun detail(cohortId: Long): CohortDetail {
        val cohort = cohorts.findById(cohortId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
        }
        val members = cohortMembers.findAllByCohortIdAndUserIdIsNotNull(cohortId)
        val memberUserIds = members.mapNotNull { it.userId }.distinct()
        val userById = users.findAllByIds(memberUserIds).associateBy { it.id }
        // Members whose User row is gone from the active query are either
        // soft-deleted (retained for stats) or hard-deleted. Flag the
        // soft-deleted ones explicitly so the admin UI can render them as
        // greyed-out / "Deleted" entries rather than the cryptic
        // "User #<id>" fallback.
        val softDeletedIds = memberUserIds
            .filter { userById[it] == null }
            .filter { users.isSoftDeleted(it) }
            .toSet()
        // The rule now lives on the cohort's subject (one fact pair per subject).
        val rules = cohort.subjectId
            ?.let { subjects.findById(it).orElse(null) }
            ?.ruleView()
            ?.let { listOf(it) }
            ?: emptyList()

        return CohortDetail(
            cohort = cohort,
            externalId = targetIds.find(cohort),
            members = members.map { member ->
                CohortMemberRow(
                    member = member,
                    user = userById[member.userId!!],
                    isUserDeleted = userById[member.userId!!] == null && softDeletedIds.contains(member.userId!!),
                )
            }.sortedWith(
                compareBy(
                    { it.isUserDeleted },  // active members first, deleted at the bottom
                    { it.user?.fullName?.lowercase() ?: "~~~" },
                ),
            ),
            rules = rules.sortedWith(compareBy({ it.factKind.name }, { it.factKey })),
        )
    }

}

/** Read-model projection of a [Cohort] for admin listings. */
data class CohortSummary(
    val cohort: Cohort,
    val memberCount: Int,
    val externalId: String?,
)

/** Detail view: the cohort itself plus its members and the rule its subject carries. */
data class CohortDetail(
    val cohort: Cohort,
    val externalId: String?,
    val members: List<CohortMemberRow>,
    val rules: List<CohortRuleView>,
)

/**
 * One row in the per-cohort members table, with the joined user record
 * if the user is still active. [isUserDeleted] is true when the user
 * has been soft-deleted but the cohort_member row was retained for
 * historical stats — the admin UI renders these in a muted style with
 * a "Deleted" badge instead of the active user details.
 */
data class CohortMemberRow(
    val member: CohortMember,
    val user: User?,
    val isUserDeleted: Boolean = false,
    /**
     * Which system's ledger this row belongs to. A row is per (cohort, user), and a cohort is
     * per system, so a subject with two targets holds two rows for the same person.
     */
    val system: TargetSystem? = null,
    /**
     * The state the row is in. Defaulted so the older per-cohort projection, which does not
     * report it, is unaffected.
     */
    val state: CohortMemberState? = null,
)

/**
 * Read projection of the membership rule a subject carries, replacing the
 * retired `CohortRule` row in read responses. [id] is the subject id — the
 * rule's identity is the subject now (one fact pair per subject).
 */
data class CohortRuleView(
    val id: Long,
    val factKind: CohortFactKind,
    val factKey: String,
    val enabled: Boolean,
)

/** The subject's rule as a [CohortRuleView], or null when its fact pair is unset (a bare CUSTOM subject). */
fun CohortSubject.ruleView(): CohortRuleView? {
    val kind = factKind ?: return null
    val key = factKey ?: return null
    return CohortRuleView(id = id!!, factKind = kind, factKey = key, enabled = enabled)
}
