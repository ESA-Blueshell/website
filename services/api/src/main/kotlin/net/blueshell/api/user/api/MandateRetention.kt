package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MandateRetentionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** A bank account no standing mandate is given for, still kept sealed. */
data class StoppedAccount(
    val userId: Long,
    /** The mandate given for this account, whose collections it may be disputed under; empty where none was. */
    val reference: String,
    val recordedOn: LocalDate,
)

/**
 * How long a bank account is kept once no mandate stands for it. Under SEPA a member can dispute a
 * debit for 13 months, so the account outlives its last use by that long and is wiped then; what
 * was collected under, the reference, the signed-on date and the masked IBAN, stays (api ADR-041).
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
    fun stopped(today: LocalDate): List<StoppedAccount> =
        mandates.findStopped(today).map { StoppedAccount(it.userId, it.reference, it.recordedAt.atZone(clock.zone).toLocalDate()) }

    /**
     * Empties the sealed IBAN, account holder and address of [stopped], and keeps the rest of it.
     * False where the mandate was recorded anew since it was judged.
     */
    @Transactional
    fun wipe(
        stopped: StoppedAccount,
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
