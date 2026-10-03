package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PendingMandateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** A mandate whose membership is no longer collected from, with its sealed bank details still kept. */
data class StoppedMandate(
    val membershipId: Long,
    val userId: Long,
    val reference: String,
)

/**
 * How long a mandate's sealed bank details are kept once collecting stops. Under SEPA a member can
 * dispute a debit for 13 months, so the details outlive the last collection by that long and are
 * wiped then; what was collected under, the reference, the signed-on date and the masked IBAN, stays.
 * When the last collection was is the contribution module's to say, so it runs the wipe.
 */
@Service
class MandateRetention(
    private val mandates: MandateRetentionRepository,
    private val pendingMandates: PendingMandateRepository,
) {
    /**
     * Every mandate still holding sealed values that is no longer collected from on [today]: its
     * membership ended, it is off incasso, it was removed, or its account was erased.
     */
    @Transactional(readOnly = true)
    fun stopped(today: LocalDate): List<StoppedMandate> =
        mandates.findStopped(today, "%@$ERASED_EMAIL_DOMAIN").map { StoppedMandate(it.id, it.userId, it.reference) }

    /** Empties the sealed IBAN, account holder and address of the mandate, and keeps the rest of it. */
    @Transactional
    fun wipe(membershipId: Long): Boolean = mandates.wipe(membershipId) == 1

    /** A pending mandate was never collected from, so it goes with its account. */
    @Transactional
    fun forgetPending(userId: Long) {
        pendingMandates.deleteByUserId(userId)
    }

    companion object {
        /** The domain an erased account's email address is rewritten to, which marks it for as long as it stays erased. */
        const val ERASED_EMAIL_DOMAIN = "deleted.invalid"
    }
}
