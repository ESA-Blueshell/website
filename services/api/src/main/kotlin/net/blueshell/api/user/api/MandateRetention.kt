package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import net.blueshell.api.user.persistence.PendingMandateRepository
import org.slf4j.LoggerFactory
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
     * membership ended, is off incasso or was removed. An erased account's memberships are off incasso.
     */
    @Transactional(readOnly = true)
    fun stopped(today: LocalDate): List<StoppedMandate> = mandates.findStopped(today).map { StoppedMandate(it.id, it.userId, it.reference) }

    /**
     * Empties the sealed IBAN, account holder and address of [stopped], and keeps the rest of it.
     * False where the mandate was recorded anew or is collected from again since it was judged.
     */
    @Transactional
    fun wipe(
        stopped: StoppedMandate,
        today: LocalDate,
    ): Boolean {
        val wiped = mandates.wipe(stopped.membershipId, stopped.reference, today) == 1
        if (wiped) log.info("[privacy] wiped the bank details of the mandate on membership {}", stopped.membershipId)
        return wiped
    }

    /**
     * An account was erased. Its memberships come off incasso, which starts the 13 months for a
     * mandate collected under, and its pending mandate, never collected from, goes at once.
     */
    @Transactional
    fun accountErased(userId: Long) {
        mandates.stopCollecting(userId)
        pendingMandates.deleteByUserId(userId)
    }

    private companion object {
        val log = LoggerFactory.getLogger(MandateRetention::class.java)
    }
}
