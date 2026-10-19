package net.blueshell.api.user.domain

import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.user.api.BankDetailsChanged
import net.blueshell.api.user.api.MandateEnded
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.persistence.BankAccount
import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PaymentDetails
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** What anybody but the board sees of a bank account: the masked IBAN and when it was put on file. */
data class BankAccountPreview(
    val iban: MaskedIban,
    val recordedAt: Instant,
)

/** Puts a new version of the bank account in place, replacing the one before it whole (api ADR-041). */
fun PaymentDetails.replaceAccount(
    sealed: SealedAccount,
    iban: Iban,
    bic: String?,
    at: Instant,
): BankAccount =
    // Counted past a mandate's version too, so a wiped account's successor is never mistaken for it.
    BankAccount(sealed.iban, sealed.accountHolder, bic, iban.masked, maxOf(bankAccount?.version ?: 0, mandate?.accountVersion ?: 0) + 1, at)
        .also { bankAccount = it }

fun BankAccount.preview(): BankAccountPreview = BankAccountPreview(requireNotNull(MaskedIban.of(ibanMasked)), recordedAt)

/** A person's one bank account: typed in whole, sealed, and shown back only as its preview. */
@Service
class BankAccounts(
    private val payments: PaymentDetailsRepository,
    private val retention: MandateRetentionRepository,
    private val sealing: SealedBankDetails,
    private val clock: Clock,
    private val events: AfterCommitEventPublisher,
) {
    @Transactional(readOnly = true)
    fun own(userId: Long): BankAccountPreview? = payments.findByUserId(userId)?.bankAccount?.preview()

    /**
     * Replaces the person's bank account whole. A mandate given for the one before it ends, since it
     * was given for that account; replacing one on file is logged and the member told, masked.
     */
    @Transactional
    fun replace(
        userId: Long,
        rawIban: String,
        accountHolder: String,
        rawBic: String?,
    ): BankAccountPreview {
        val iban = Iban.parse(rawIban) ?: throw InvalidIban()
        val holder = accountHolder.trim().ifEmpty { throw AccountHolderMissing() }
        val bic = bicFor(iban, rawBic)
        val details = payments.findByUserId(userId) ?: PaymentDetails(userId)
        val replacing = details.bankAccount != null
        details.replaceAccount(sealing.seal(userId, iban, holder), iban, bic, clock.instant())
        endMandateOf(details)
        val saved = payments.save(details)
        retention.clearMandateCopy(userId)
        if (replacing) events.publish(BankDetailsChanged(userId, requireNotNull(MaskedIban.of(iban.masked))))
        return requireNotNull(saved.bankAccount).preview()
    }

    /** Deletes the person's bank account; a mandate given for it ends. */
    @Transactional
    fun delete(userId: Long) {
        val details = payments.findByUserId(userId) ?: return
        details.bankAccount = null
        // The address an online mandate was authorised under goes with the account it was given for.
        details.mandate?.sealedAddress = null
        endMandateOf(details)
        payments.save(details)
        retention.clearMandateCopy(userId)
    }

    private fun endMandateOf(details: PaymentDetails) {
        val today = LocalDate.now(clock)
        val mandate = details.mandate?.takeIf { it.standsOn(today) && it.accountVersion != null && !details.mandateAccountKept } ?: return
        mandate.endedOn = today
        events.publish(MandateEnded(details.userId, mandate.reference))
    }

    // Within the Netherlands the IBAN names the bank, so a BIC there is not kept. TWIN: `needsBic` and
    // `bicProblem` in the frontend's `domains/banking/bankAccount.ts`.
    private fun bicFor(
        iban: Iban,
        raw: String?,
    ): String? {
        if (iban.value.startsWith(DUTCH)) return null
        val bic = raw?.replace(" ", "")?.uppercase()?.ifEmpty { null } ?: throw BicMissing()
        if (!BIC.matches(bic)) throw InvalidBic()
        return bic
    }

    private companion object {
        const val DUTCH = "NL"
        val BIC = Regex("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$")
    }
}
