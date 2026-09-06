package net.blueshell.api.cohort.domain

import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.cohort.persistence.CohortSubjectType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

data class MembershipPreview(val alreadyMember: Boolean)

enum class MembershipWriteStatus {
    WRITTEN,
    NOOP_ALREADY_TRUE,
    UNSUPPORTED,
    SKIPPED_UNMATCHED,
    SKIPPED_MAPPING_CONFLICT,
    FAILED,
}

/**
 * Makes somebody a member of a cohort by changing what the cohort is about.
 *
 * Cohort membership is derived, so it cannot be granted directly: somebody found on a Brevo
 * list who is not in the cohort is not made a member by writing a row, but by making the
 * thing true that the cohort is defined by — recording the contribution they evidently paid.
 * Only some cohorts can be written into at all; the rest report that they cannot.
 */
interface MembershipWriter {
    val type: CohortSubjectType

    fun preview(userId: Long, definition: CohortDefinition): MembershipPreview

    fun apply(userId: Long, definition: CohortDefinition): MembershipWriteStatus
}

@Component
class MembershipWriters(writers: List<MembershipWriter>) {
    private val byType = writers.associateBy { it.type }

    fun find(type: CohortSubjectType): MembershipWriter? = byType[type]
}

/** Being in the paid cohort is having paid, so joining it is a contribution being recorded. */
@Component
class ContributionPaidWriter(
    private val contributions: ContributionService,
    private val reconciliation: CohortReconciliationService,
) : MembershipWriter {
    override val type: CohortSubjectType = CohortSubjectType.PERIOD_PAYERS

    override fun preview(userId: Long, definition: CohortDefinition): MembershipPreview =
        MembershipPreview(definition.contains(userId))

    override fun apply(userId: Long, definition: CohortDefinition): MembershipWriteStatus {
        if (preview(userId, definition).alreadyMember) {
            reconciliation.evaluateUserCohorts(userId)
            return MembershipWriteStatus.NOOP_ALREADY_TRUE
        }
        val periodId = definition.scope
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "${definition.key} is about no period")
        val created = contributions.ensurePaid(userId, periodId)
        reconciliation.evaluateUserCohorts(userId)
        return if (created) MembershipWriteStatus.WRITTEN else MembershipWriteStatus.NOOP_ALREADY_TRUE
    }
}
