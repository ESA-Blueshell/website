package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.api.IncassoFileDownloaded
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.persistence.IncassoRunRepository
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.user.api.CollectionAccounts
import net.blueshell.api.user.api.PaymentDirectory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** One of a run's files for ING, named for its collection date. */
class IncassoFile(
    val name: String,
    val bytes: ByteArray,
)

/**
 * A run's batch files for ING, filled in when downloaded and never written anywhere: the full
 * account numbers exist only in the answer. A file is refused once the run is in ING, after its
 * collection date, and where a member's mandate changed since they were told.
 */
@Service
class IncassoFiles(
    private val runs: IncassoRunRepository,
    private val notifications: IncassoNotificationRepository,
    private val payments: PaymentDirectory,
    private val accounts: CollectionAccounts,
    private val contributions: ContributionService,
    private val bank: BankProperties,
    private val clock: Clock,
    // After commit: a download that fails is not logged as one.
    private val events: AfterCommitEventPublisher,
) {
    /** The file for [downloadedBy], whose security log records the download before the file is answered. */
    @Transactional(readOnly = true)
    fun file(
        runId: Long,
        part: Int,
        downloadedBy: Long,
    ): IncassoFile {
        val run = runs.findById(runId).orElseThrow { IncassoRunNotFound() }
        if (run.submittedAt != null) throw IncassoRunSubmitted()
        if (!run.collectionDate.isAfter(LocalDate.now(clock))) throw CollectionDatePassed()
        val header =
            IngHeader(
                accountHolder = ingText(bank.accountName).take(NAME_MAX),
                iban = bank.iban.replace(" ", ""),
                incassantId = bank.incassantId.replace(" ", ""),
                collectionDate = run.collectionDate,
            )
        if (header.iban.length != HEADER_IBAN_LENGTH || header.incassantId.length != INCASSANT_ID_LENGTH) throw IngDetailsMissing()

        // By name, then id, so every part is cut from the same order.
        val all = notifications.findByIncassoRunIdIn(listOf(runId)).sortedWith(compareBy({ it.user.fullName }, { it.userId }))
        val chunks = all.chunked(IngIncassoFile.MAX_COLLECTIONS)
        if (part < 1) throw IncassoFilePartNotFound()
        val inPart = chunks.getOrNull(part - 1) ?: throw IncassoFilePartNotFound()
        val paid = contributions.findByContributionPeriodId(run.contributionPeriodId).map { it.userId }.toSet()
        val paying = payments.of(inPart.map { it.userId })
        // Somebody who since moved to transfer or paid is not collected from, whatever they were told.
        val theirs = inPart.filter { paying.getValue(it.userId).incasso && it.userId !in paid }
        val mandates =
            theirs.associate { told ->
                told.userId to paying.getValue(told.userId).collectable?.takeIf { it.reference == told.mandateReference }
            }
        val changed = mandates.filterValues { it == null }.keys.sorted()
        if (changed.isNotEmpty()) throw MandateChanged(changed)

        // One call opens the whole part, so a file costs Vault one request however many rows it has.
        val opened = accounts.of(theirs.map { told -> told.userId to requireNotNull(mandates[told.userId]) })
        check(opened.size == theirs.size) { "Opened ${opened.size} accounts for ${theirs.size} collections" }
        val collections =
            theirs.zip(opened).map { (told, account) ->
                IngCollection(
                    name = ingText(account.accountHolder).take(NAME_MAX),
                    iban = account.iban,
                    mandateReference = ingText(requireNotNull(told.mandateReference)),
                    amount = told.amount,
                    description = run.statementText,
                    mandateSignedOn = requireNotNull(told.mandateSignedOn),
                )
            }
        events.publish(IncassoFileDownloaded(downloadedBy, runId, part, chunks.size, theirs.size))
        val suffix = if (chunks.size > 1) "-$part-of-${chunks.size}" else ""
        return IncassoFile("incassobatch-${run.collectionDate}$suffix.xlsx", IngIncassoFile.write(header, collections))
    }

    private companion object {
        const val NAME_MAX = 70
        const val HEADER_IBAN_LENGTH = 18
        const val INCASSANT_ID_LENGTH = 19
    }
}
