package net.blueshell.api.contribution.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.persistence.IncassoRun
import net.blueshell.api.contribution.persistence.IncassoRunRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.Membership
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Why a member on incasso is not collected from, in the order it is decided. */
@Schema(name = "IncassoLeftOut", enumAsRef = true)
enum class IncassoLeftOut {
    OWES_NOTHING,
    DELETED,
    NO_EMAIL,
    NO_BANK_DETAILS,
    ALREADY_PAID,
}

/** One member on incasso in a period, masked to the last four, and whether they can be collected from. */
data class IncassoCandidate(
    val userId: Long,
    @field:Schema(description = "The membership the member is judged on, which their incasso details are recorded against.")
    val membershipId: Long,
    val name: String,
    /** The name as it goes to ING, accents stripped. */
    val ingName: String,
    val memberSince: LocalDate,
    val feeType: BulkFeeType?,
    val amount: Double?,
    @field:Schema(description = "The IBAN's country code; with the last two, all a response carries of it.")
    val ibanCountry: String?,
    val ibanLastTwo: String?,
    val mandateReference: String?,
    val mandateSignedOn: LocalDate?,
    val leftOut: IncassoLeftOut?,
    val lastNotifiedOn: LocalDate?,
)

/** One collection of a run, as the member was told it. */
data class IncassoCollection(
    val userId: Long,
    val name: String,
    val ingName: String,
    @field:Schema(description = "The IBAN's country code; with the last two, all a response carries of it.")
    val ibanCountry: String?,
    val ibanLastTwo: String?,
    val mandateReference: String?,
    val mandateSignedOn: LocalDate?,
    val feeType: BulkFeeType,
    val amount: Double,
)

data class IncassoRunView(
    val id: Long,
    val contributionPeriodId: Long,
    val collectionDate: LocalDate,
    val statementText: String,
    val collections: List<IncassoCollection>,
    val total: Double,
    val createdAt: Instant,
    /** Null while the run waits for upload to ING. */
    val submittedAt: Instant?,
    /** How many files ING's batch takes, at most 1000 collections each. */
    val fileParts: Int,
)

data class IncassoRunSummary(
    val id: Long,
    val collectionDate: LocalDate,
    val collections: Int,
    val total: Double,
    val submittedAt: Instant?,
)

/**
 * Collecting a period's contributions by incasso: who can be collected from, then a run that
 * tells each of them the date, amount and account before ING takes it.
 */
@Service
class IncassoRuns(
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val memberships: MembershipService,
    private val erasure: UserErasureService,
    private val notifications: IncassoNotificationService,
    private val notificationRows: IncassoNotificationRepository,
    private val runs: IncassoRunRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun plan(periodId: Long): List<IncassoCandidate> = candidates(periods.findById(periodId)).map { it.second }

    /** Records the run and one notification per member, each queued to send once this commits. */
    @Transactional
    fun start(
        periodId: Long,
        userIds: Collection<Long>,
        feeTypeOverrides: Map<Long, BulkFeeType>,
        collectionDate: LocalDate,
        statementText: String,
        by: Long?,
    ): IncassoRunView {
        val period = periods.findById(periodId)
        val chosen = userIds.distinct()
        if (chosen.isEmpty()) throw NothingToCollect()
        val candidates = candidates(period).associateBy { it.second.userId }
        val refused = chosen.filter { candidates[it]?.second?.leftOut != null || it !in candidates }
        if (refused.isNotEmpty()) throw NotCollectable(refused.sorted())
        if (!collectionDate.isAfter(LocalDate.now(clock))) throw CollectionDateNotAhead()
        if (collectionDate < period.startDate || collectionDate > period.endDate.plusMonths(MONTHS_PAST_PERIOD_END)) {
            throw CollectionDateOutsidePeriod()
        }
        val text = ingText(statementText).ifEmpty { throw StatementTextMissing() }
        if (text.length > STATEMENT_TEXT_MAX) throw StatementTextTooLong(STATEMENT_TEXT_MAX)

        val now = clock.instant()
        val run = runs.save(IncassoRun(periodId, collectionDate, text, by, now))
        for (userId in chosen) {
            val (membership, candidate) = candidates.getValue(userId)
            val feeType = feeTypeOverrides[userId] ?: requireNotNull(candidate.feeType)
            notifications.record(
                IncassoNotification(
                    user = membership.user,
                    contributionPeriod = period,
                    feeType = feeType,
                    amount = resolveFeeAmount(feeType, period),
                    debitDate = collectionDate,
                    askedAt = now,
                    incassoRunId = run.id,
                    mandateReference = candidate.mandateReference,
                    mandateSignedOn = candidate.mandateSignedOn,
                    ibanMasked = candidate.ibanCountry?.let { it + candidate.ibanLastTwo },
                ),
            )
        }
        return view(run)
    }

    @Transactional(readOnly = true)
    fun find(runId: Long): IncassoRunView = view(runs.findById(runId).orElseThrow { IncassoRunNotFound() })

    /** The board uploaded the run's file in ING and confirmed it there. Saying so twice changes nothing. */
    @Transactional
    fun markSubmitted(
        runId: Long,
        by: Long?,
    ): IncassoRunView {
        val run = runs.findById(runId).orElseThrow { IncassoRunNotFound() }
        if (run.submittedAt == null) {
            run.submittedAt = clock.instant()
            run.submittedBy = by
            runs.save(run)
        }
        return view(run)
    }

    @Transactional(readOnly = true)
    fun summariesOf(periodId: Long): List<IncassoRunSummary> {
        val theirs = runs.findByContributionPeriodIdOrderByCreatedAtDesc(periodId)
        val sent = notificationRows.findByIncassoRunIdIn(theirs.mapNotNull { it.id }).groupBy { it.incassoRunId }
        return theirs.map { run ->
            val collections = sent[run.id].orEmpty()
            IncassoRunSummary(
                id = requireNotNull(run.id),
                collectionDate = run.collectionDate,
                collections = collections.size,
                total = collections.sumOf { it.amount },
                submittedAt = run.submittedAt,
            )
        }
    }

    private fun view(run: IncassoRun): IncassoRunView {
        val collections =
            notificationRows
                .findByIncassoRunIdIn(listOf(requireNotNull(run.id)))
                .map {
                    IncassoCollection(
                        userId = it.userId,
                        name = it.user.fullName,
                        ingName = ingText(it.user.fullName),
                        ibanCountry = MaskedIban.of(it.ibanMasked)?.country,
                        ibanLastTwo = MaskedIban.of(it.ibanMasked)?.lastTwo,
                        mandateReference = it.mandateReference,
                        mandateSignedOn = it.mandateSignedOn,
                        feeType = it.feeType,
                        amount = it.amount,
                    )
                }.sortedBy { it.name }
        return IncassoRunView(
            id = requireNotNull(run.id),
            contributionPeriodId = run.contributionPeriodId,
            collectionDate = run.collectionDate,
            statementText = run.statementText,
            collections = collections,
            total = collections.sumOf { it.amount },
            createdAt = run.createdAt,
            submittedAt = run.submittedAt,
            fileParts = (collections.size + IngIncassoFile.MAX_COLLECTIONS - 1) / IngIncassoFile.MAX_COLLECTIONS,
        )
    }

    /** Everybody whose membership in the period is on incasso, with the membership they are judged on. */
    private fun candidates(period: ContributionPeriod): List<Pair<Membership, IncassoCandidate>> {
        val paid = contributions.findByContributionPeriodId(requireNotNull(period.id)).map { it.userId }.toSet()
        val lastNotified =
            notifications
                .findByContributionPeriodId(requireNotNull(period.id))
                .groupBy { it.userId }
                .mapValues { (_, sent) -> sent.maxOf { it.askedAt }.atZone(ZoneOffset.UTC).toLocalDate() }
        val judged =
            memberships
                .findOverlappingWithMembers(period.startDate, period.endDate)
                .groupBy { it.userId }
                .mapValues { (_, held) -> held.filter { it.endDate == null }.maxByOrNull { it.startDate } ?: held.maxBy { it.startDate } }
                .values
                // Off incasso is not collected from, whatever mandate is still on file.
                .filter { it.incasso }
        val deleted = erasure.deletedIdsAmong(judged.map { it.userId })
        return judged
            .map { membership ->
                val feeType = resolveFeeType(membership.memberType, membership.startDate, period)
                val mandate = membership.mandate?.takeUnless { it.wiped }
                val leftOut =
                    when {
                        feeType == null -> IncassoLeftOut.OWES_NOTHING
                        membership.userId in deleted -> IncassoLeftOut.DELETED
                        membership.user.email.isBlank() -> IncassoLeftOut.NO_EMAIL
                        mandate == null -> IncassoLeftOut.NO_BANK_DETAILS
                        membership.userId in paid -> IncassoLeftOut.ALREADY_PAID
                        else -> null
                    }
                membership to
                    IncassoCandidate(
                        userId = membership.userId,
                        membershipId = requireNotNull(membership.id),
                        name = membership.user.fullName,
                        ingName = ingText(membership.user.fullName),
                        memberSince = membership.startDate,
                        feeType = feeType,
                        amount = feeType?.let { resolveFeeAmount(it, period) },
                        ibanCountry = MaskedIban.of(mandate?.ibanMasked)?.country,
                        ibanLastTwo = MaskedIban.of(mandate?.ibanMasked)?.lastTwo,
                        mandateReference = mandate?.reference,
                        mandateSignedOn = mandate?.signedOn,
                        leftOut = leftOut,
                        lastNotifiedOn = lastNotified[membership.userId],
                    )
            }.sortedBy { it.second.name }
    }

    companion object {
        /** ING's limit on the text on a bank statement. */
        const val STATEMENT_TEXT_MAX = 140

        /** Mirrors `MONTHS_PAST_PERIOD_END` for the payment emails: a collection chasing the last members may run past the period. */
        const val MONTHS_PAST_PERIOD_END = 3L
    }
}
