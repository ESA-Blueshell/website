package net.blueshell.api.pinger.api

import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.random.Random

/** How often the demo advances; the pps a device reports is its bump over this interval. */
private const val TICK_MS = 2000L
private const val TICK_SECONDS = 2

/**
 * One fabricated device: who it reports as, its per-install id, its rate, and its own cumulative
 * counter. The counter is this process's, so an api restart resets it to zero and the accrual
 * treats that as the device's own session restarting — exactly the real reset path.
 */
private class DemoDevice(
    val identity: PingerIdentity,
    val deviceId: String,
    val rate: Int,
) {
    var sent: Long = 0
}

/**
 * Makes the dev leaderboard move on its own, so the SNTPings tab shows pings growing, per-row rates
 * ticking and members overtaking without a real pinger.
 *
 * Dev only and off unless `app.pinger-demo.enabled` is set, so an ordinary dev database stays still
 * and only the preview stack ticks. It drives the real report path — one `POST /pinger/report`
 * worth per device per tick — rather than writing totals directly, so it exercises the actual
 * per-device accrual: SiteCie reports from two replicas and one member (member.paid) from two
 * devices, and the board shows their totals summing and their rates adding across devices.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.pinger-demo", name = ["enabled"], havingValue = "true")
// The demo fleet's seed rates are the data of this dev-only driver, not constants worth naming.
@Suppress("MagicNumber")
class DevPingerActivity(
    private val reports: PingerReportService,
    private val users: UserService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Built lazily once the seeded accounts exist; empty until then and after a resolve that found none. */
    @Volatile
    private var devices: List<DemoDevice>? = null

    init {
        log.info("[dev-pinger-demo] live leaderboard demo is on; devices report every {}ms", TICK_MS)
    }

    @Scheduled(fixedDelay = TICK_MS)
    fun tick() {
        val fleet = devices ?: build().also { devices = it }
        fleet.forEach { device ->
            device.sent += device.rate.toLong() * TICK_SECONDS
            runCatching {
                reports.report(
                    identity = device.identity,
                    deviceId = device.deviceId,
                    online = true,
                    pps = device.rate,
                    sent = device.sent,
                )
            }.onFailure { log.debug("[dev-pinger-demo] report failed for {}", device.deviceId, it) }
        }
    }

    /**
     * The fabricated fleet: SiteCie across two replicas, each member on one device, and member.paid
     * on two so its total and rate aggregate across devices. A lower rate on the lower-seeded
     * members lets the tail overtake over a minute. A member whose account is not seeded is skipped.
     */
    private fun build(): List<DemoDevice> {
        val fleet = mutableListOf<DemoDevice>()
        fleet += DemoDevice(PingerIdentity.Sitecie, "sitecie-replica-a", 9_000)
        fleet += DemoDevice(PingerIdentity.Sitecie, "sitecie-replica-b", 8_200)

        memberDevice("committee", "committee-desktop", 90)?.let(fleet::add)
        memberDevice("member.alumni", "alumni-desktop", 150)?.let(fleet::add)
        memberDevice("member.unpaid", "unpaid-desktop", 230)?.let(fleet::add)
        memberDevice("member.honorary", "honorary-desktop", 310)?.let(fleet::add)
        // member.paid runs on two devices at once, to show one member's total and rate aggregating.
        memberDevice("member.paid", "paid-laptop", 170)?.let(fleet::add)
        memberDevice("member.paid", "paid-phone", 130)?.let(fleet::add)

        if (fleet.isNotEmpty()) log.info("[dev-pinger-demo] driving {} demo devices", fleet.size)
        return fleet
    }

    private fun memberDevice(
        username: String,
        deviceId: String,
        rate: Int,
    ): DemoDevice? {
        val id = runCatching { users.findByUsername(username).id }.getOrNull() ?: return null
        // A little jitter per device so the climb is not lockstep and ranks reshuffle believably.
        return DemoDevice(PingerIdentity.Member(id), deviceId, rate + Random.nextInt(40))
    }
}
