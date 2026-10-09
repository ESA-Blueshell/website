package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.pinger.persistence.PingerRecord
import net.blueshell.api.pinger.persistence.PingerRecordRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Keeps the combined-rate record: about once a second it sums every online sender's live rate,
 * SiteCie included, and raises the record when that beats it. It writes only on a new high, and
 * remembers the best it knows so a quiet second costs no database read.
 */
@Component
class PingerRecordKeeper(
    private val records: PingerRecordRepository,
    private val live: PingerLiveStore,
    private val clock: Clock,
) {
    @Volatile
    private var known: Long? = null

    @Scheduled(fixedDelayString = $$"${pinger.record.check-interval-ms:1000}")
    @Transactional
    fun tick() {
        observe(live.aggregateAll().combinedPps())
    }

    /** Raises the record to [combined] when it beats the stored one; a lower or equal rate writes nothing. */
    fun observe(combined: Long) {
        val best = known ?: stored()
        if (combined <= best) {
            known = best
            return
        }
        val at = clock.instant()
        known =
            when {
                records.raise(combined, at) > 0 -> combined
                // Another replica already holds a higher record, so that one is the one to remember.
                records.existsById(PingerRecordRepository.RECORD_ID) -> stored()
                // The migration seeds the row; a wiped table is set afresh rather than never again.
                else -> records.save(PingerRecord(pps = combined, setAt = at)).pps
            }
    }

    private fun stored(): Long = records.findById(PingerRecordRepository.RECORD_ID).map { it.pps }.orElse(0L)
}
