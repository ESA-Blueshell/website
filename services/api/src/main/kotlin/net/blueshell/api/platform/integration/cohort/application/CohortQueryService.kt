package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortRule
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRuleRepository
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
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
    private val cohortRules: CohortRuleRepository,
    private val users: UserService,
    private val externalIds: ExternalIdMappingService,
) {
    @Transactional(readOnly = true)
    fun summaries(): List<CohortSummary> =
        cohorts.findAll().map { cohort ->
            CohortSummary(
                cohort = cohort,
                memberCount = cohortMembers.findAllByCohortId(cohort.id!!).size,
                externalId = externalIds.find(COHORT_AGGREGATE, cohort.id!!, cohort.system)?.externalId,
            )
        }

    @Transactional(readOnly = true)
    fun detail(cohortId: Long): CohortDetail {
        val cohort = cohorts.findById(cohortId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Cohort $cohortId not found")
        }
        val members = cohortMembers.findAllByCohortId(cohortId)
        val userById = users.findAllByIds(members.map { it.userId }.distinct()).associateBy { it.id }
        val rules = cohortRules.findAllByCohortId(cohortId)

        return CohortDetail(
            cohort = cohort,
            externalId = externalIds.find(COHORT_AGGREGATE, cohortId, cohort.system)?.externalId,
            members = members.map { member ->
                CohortMemberRow(
                    member = member,
                    user = userById[member.userId],
                )
            }.sortedBy { it.user?.fullName?.lowercase() ?: "~~~" },
            rules = rules.sortedWith(compareBy({ it.factKind.name }, { it.factKey })),
        )
    }

    companion object {
        private const val COHORT_AGGREGATE = "COHORT"
    }
}

/** Read-model projection of a [Cohort] for admin listings. */
data class CohortSummary(
    val cohort: Cohort,
    val memberCount: Int,
    val externalId: String?,
)

/** Detail view: the cohort itself plus its members and the rules that target it. */
data class CohortDetail(
    val cohort: Cohort,
    val externalId: String?,
    val members: List<CohortMemberRow>,
    val rules: List<CohortRule>,
)

/** One row in the per-cohort members table, with the joined user record. */
data class CohortMemberRow(
    val member: CohortMember,
    val user: User?,
)
