package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.port.`in`.CohortReconciliation
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

data class SubjectFact(val kind: CohortFactKind, val key: String)

fun SubjectFact.toUserFact(): UserFact = UserFact(kind, key)

data class FactPreview(val alreadyTrue: Boolean)

enum class FactWriteStatus { WRITTEN, NOOP_ALREADY_TRUE, UNSUPPORTED, SKIPPED_UNMATCHED, SKIPPED_MAPPING_CONFLICT, FAILED }

interface FactWriter {
    val kind: CohortFactKind
    fun preview(userId: Long, fact: SubjectFact): FactPreview
    fun apply(userId: Long, fact: SubjectFact): FactWriteStatus
}

@Component
class FactWriters(writers: List<FactWriter>) {
    private val byKind = writers.associateBy { it.kind }

    fun find(kind: CohortFactKind): FactWriter? = byKind[kind]
}

@Component
class ContributionPaidWriter(
    private val facts: UserFactCollector,
    private val contributions: ContributionService,
    private val reconciliation: CohortReconciliation,
) : FactWriter {
    override val kind: CohortFactKind = CohortFactKind.CONTRIBUTION_PAID

    override fun preview(userId: Long, fact: SubjectFact): FactPreview =
        FactPreview(facts.collect(userId).contains(fact.toUserFact()))

    override fun apply(userId: Long, fact: SubjectFact): FactWriteStatus {
        if (preview(userId, fact).alreadyTrue) {
            reconciliation.evaluateUserCohorts(userId)
            return FactWriteStatus.NOOP_ALREADY_TRUE
        }
        val periodId = fact.key.toLongOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution period id must be numeric")
        val created = contributions.ensurePaid(userId, periodId)
        reconciliation.evaluateUserCohorts(userId)
        return if (created) FactWriteStatus.WRITTEN else FactWriteStatus.NOOP_ALREADY_TRUE
    }
}
