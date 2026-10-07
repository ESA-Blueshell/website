package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Ingests a pinger's status report: it accrues the identity's durable, monotonic tally and refreshes
 * their live presence.
 *
 * The durable row is the single source of truth. The client reports its own send counter, which
 * resets to 0 whenever the app restarts, so the tally is accrued from the delta against the counter
 * we last saw rather than trusted as a running total; see [report] for the invariant.
 */
@Service
class PingerReportService(
    private val contributions: PingerContributionRepository,
    private val live: PingerLiveStore,
    private val clock: Clock,
) {
    @Transactional
    fun report(
        identity: PingerIdentity,
        online: Boolean,
        pps: Int,
        sent: Long,
    ) {
        val now = clock.instant()
        val row =
            contributions.lockByIdentity(identity.key)
                ?: PingerContribution(identity = identity.key, memberId = identity.memberId, updated = now)
        // Invariant: total_sent only ever grows. A counter at or above the one we last saw adds only
        // the delta since then; a lower counter is a restarted session and adds in full. The last
        // counter lives in this durable row, so a Valkey wipe cannot make a continuing session
        // re-add what it already accrued.
        val delta = if (sent >= row.lastSessionSent) sent - row.lastSessionSent else sent
        row.totalSent += delta
        row.lastSessionSent = sent
        row.updated = now
        contributions.save(row)
        live.touch(identity.key, online, pps, now)
    }
}
