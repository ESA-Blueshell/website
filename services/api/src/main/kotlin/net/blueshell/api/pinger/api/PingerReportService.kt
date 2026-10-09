package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerDeviceSession
import net.blueshell.api.pinger.persistence.PingerDeviceSessionRepository
import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The desktop app's own top rate, about a 1 Gbit/s uplink of echo requests; a report above it is refused. */
const val MAX_REPORT_PPS = 2_192_982L

/**
 * Ingests a pinger's status report: it accrues the identity's durable, monotonic tally and refreshes
 * the reporting device's live presence.
 *
 * A member may run the app on several devices at once, each with its own send counter that resets
 * when that device's app restarts. The durable total is one row per identity; the last-seen counter
 * is one row per (identity, device). Each report adds only its own device's positive delta, so two
 * devices never clobber each other, a reset on one device starts that device fresh without touching
 * the others, and the member's single total only ever grows. SiteCie's replicas aggregate the same
 * way under the one `sitecie` identity. The identity's peak rate rises in the same transaction.
 */
@Service
class PingerReportService(
    private val contributions: PingerContributionRepository,
    private val devices: PingerDeviceSessionRepository,
    private val live: PingerLiveStore,
    private val clock: Clock,
) {
    @Transactional
    fun report(
        identity: PingerIdentity,
        deviceId: String,
        online: Boolean,
        pps: Int,
        sent: Long,
    ) {
        val now = clock.instant()
        // Lock the identity's total first, so two devices of one member serialise their increments
        // onto it in a consistent order rather than racing a read-modify-write.
        val contribution =
            contributions.lockByIdentity(identity.key)
                ?: PingerContribution(identity = identity.key, memberId = identity.memberId, updated = now)
        val existing = devices.lockByIdentityAndDevice(identity.key, deviceId)
        // A new device for this identity: cap the rows a client can mint from spoofed device ids by
        // evicting the least recently seen once the identity is at the limit. A handful of real
        // devices never trips it, and the live presence keys age out on their own TTL regardless.
        if (existing == null && devices.countByIdentity(identity.key) >= MAX_DEVICES_PER_IDENTITY) {
            devices.findFirstByIdentityOrderByUpdatedAsc(identity.key)?.let(devices::delete)
        }
        val session = existing ?: PingerDeviceSession(identity = identity.key, deviceId = deviceId, updated = now)
        // Invariant: total_sent only ever grows. A device counter at or above the one we last saw
        // for THAT device adds only its delta; a lower counter is that device's restarted session
        // and adds in full. The marker is per device, so one device resetting cannot make another
        // re-add what it already accrued.
        val raw = if (sent >= session.lastSessionSent) sent - session.lastSessionSent else sent
        // Clamp one report's contribution to what a sender at the capped rate could reach since the
        // device last reported, so a client cannot leap the board with a counter no real run could
        // produce, while reports a stalled uplink held back still count once they get through.
        val window = existing?.let { Duration.between(it.updated, now).seconds }?.coerceAtLeast(MIN_WINDOW_SECONDS)
        val delta = minOf(raw, MAX_REPORT_PPS * (window ?: MIN_WINDOW_SECONDS))
        contribution.totalSent += delta
        contribution.updated = now
        session.lastSessionSent = sent
        session.updated = now
        live.touch(identity.key, deviceId, online, pps, now)
        if (online) raisePeak(contribution, identity, now)
        contributions.save(contribution)
        devices.save(session)
    }

    // The peak is the identity's rate across all its online devices, read after this device's own
    // presence is written, so a member on two machines counts both.
    private fun raisePeak(
        contribution: PingerContribution,
        identity: PingerIdentity,
        now: Instant,
    ) {
        val here = live.aggregate(identity.key)?.takeIf { it.online } ?: return
        val across = here.pps.toLong()
        if (across > contribution.peakPps) {
            contribution.peakPps = across
            contribution.peakAt = now
        }
    }

    private companion object {
        // Enough for a member's real machines; a client spoofing distinct ids past this evicts its
        // own oldest rather than growing the table without bound.
        const val MAX_DEVICES_PER_IDENTITY = 10L

        // A report covers at least this long, so a first report or two close together are not
        // clamped below what a real sender produced between them.
        const val MIN_WINDOW_SECONDS = 60L
    }
}
