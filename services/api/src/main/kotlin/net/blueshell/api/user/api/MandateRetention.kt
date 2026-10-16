package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** A mandate no longer collected from, with its sealed bank details still kept. */
data class StoppedMandate(
    val userId: Long,
    val reference: String,
)

/**
 * How long a mandate's sealed bank details are kept once collecting stops. Under SEPA a member can
 * dispute a debit for 13 months, so the details outlive the last collection by that long and are
 * wiped then; what was collected under, the reference, the signed-on date and the masked IBAN, stays.
 */
@Service
class MandateRetention(
    private val mandates: MandateRetentionRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Every mandate still holding sealed values that ended by [today]: stopped, replaced, ended with
     * its membership or with an erased account.
     */
    @Transactional(readOnly = true)
    fun stopped(today: LocalDate): List<StoppedMandate> = mandates.findStopped(today).map { StoppedMandate(it.userId, it.reference) }

    /**
     * Empties the sealed IBAN, account holder and address of [stopped], and keeps the rest of it.
     * False where the mandate was recorded anew since it was judged.
     */
    @Transactional
    fun wipe(
        stopped: StoppedMandate,
        today: LocalDate,
    ): Boolean {
        val wiped = mandates.wipe(stopped.userId, stopped.reference, today) == 1
        if (wiped) log.info("[privacy] wiped the bank details of the mandate of user {}", stopped.userId)
        return wiped
    }

    /**
     * An account was erased. Its mandate ends today, which starts the 13 months for one collected
     * under; one never collected from goes at the next wipe.
     */
    @Transactional
    fun accountErased(userId: Long) {
        mandates.stopCollecting(userId, LocalDate.now(clock))
    }

    private companion object {
        val log = LoggerFactory.getLogger(MandateRetention::class.java)
    }
}
