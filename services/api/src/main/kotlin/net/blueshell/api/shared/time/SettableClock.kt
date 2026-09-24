package net.blueshell.api.shared.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A clock a test can stop and move, so a rule is asked about the second before and after its
 * boundary instead of being waited on (testing ADR-008). Only the test profile has one.
 */
class SettableClock(
    private val zone: ZoneId = ZoneOffset.UTC,
    private val base: Clock = Clock.system(zone),
) : Clock() {
    @Volatile
    private var fixed: Instant? = null

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = SettableClock(zone, base.withZone(zone)).also { it.fixed = fixed }

    override fun instant(): Instant = fixed ?: base.instant()

    fun set(instant: Instant) {
        fixed = instant
    }

    fun advance(by: Duration) {
        fixed = instant().plus(by)
    }

    fun reset() {
        fixed = null
    }
}
