package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveDevice
import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PingerShareServiceTest {
    private val start = Instant.parse("2026-10-09T20:00:00Z")
    private val clock = MutableClock(start)
    private val live = mockk<PingerLiveStore>()
    private val service = PingerShareService(live, clock)

    private fun device(
        identity: String,
        deviceId: String,
        pps: Int,
        online: Boolean = true,
    ) = PingerLiveDevice(identity, deviceId, PingerLive(online = online, pps = pps, lastSeen = start))

    private fun liveDevices(vararg devices: PingerLiveDevice) {
        every { live.devices() } returns devices.toList()
    }

    @Test
    fun `a lone device paints everything`() {
        liveDevices()

        assertThat(service.share("member:1", "laptop")).isEqualTo(PingerShare(from = 0.0, to = 1.0, devices = 1))
    }

    @Test
    fun `a lone online device that is the requester paints everything`() {
        liveDevices(device("member:1", "laptop", 5_000))

        assertThat(service.share("member:1", "laptop")).isEqualTo(PingerShare(from = 0.0, to = 1.0, devices = 1))
    }

    @Test
    fun `slices are proportional to each device's rate`() {
        liveDevices(device("member:1", "a", 30_000), device("member:2", "b", 10_000))

        val first = service.share("member:1", "a")
        val second = service.share("member:2", "b")

        assertThat(first).isEqualTo(PingerShare(from = 0.0, to = 0.75, devices = 2))
        assertThat(second).isEqualTo(PingerShare(from = 0.75, to = 1.0, devices = 2))
    }

    @Test
    fun `an idle or slow device still gets the floor's weight`() {
        liveDevices(device("member:1", "a", 0), device("member:2", "b", 3_000))

        assertThat(service.share("member:1", "a")).isEqualTo(PingerShare(from = 0.0, to = 0.25, devices = 2))
    }

    @Test
    fun `an offline device takes no slice`() {
        liveDevices(device("member:1", "a", 9_000, online = false), device("member:2", "b", 3_000))

        assertThat(service.share("member:2", "b")).isEqualTo(PingerShare(from = 0.0, to = 1.0, devices = 1))
    }

    @Test
    fun `a requester not yet online is added at the floor`() {
        liveDevices(device("member:1", "a", 3_000))

        val share = service.share("member:2", "new")

        assertThat(share).isEqualTo(PingerShare(from = 0.75, to = 1.0, devices = 2))
    }

    @Test
    fun `order is by identity then device, whatever order the store lists them in`() {
        liveDevices(device("sitecie", "r1", 1_000), device("member:1", "b", 1_000), device("member:1", "a", 1_000))

        assertThat(service.share("member:1", "a").from).isEqualTo(0.0)
        assertThat(service.share("member:1", "b").from).isCloseTo(1.0 / 3, within(1e-12))
        assertThat(service.share("sitecie", "r1").from).isCloseTo(2.0 / 3, within(1e-12))
    }

    @Test
    fun `the slices tile the unit interval and the last ends at exactly one`() {
        val devices = (1..7).map { device("member:$it", "d", 1_000 + it * 1_337) }
        liveDevices(*devices.toTypedArray())

        val shares = devices.map { service.share(it.identity, it.deviceId) }

        assertThat(shares.first().from).isEqualTo(0.0)
        shares.zipWithNext().forEach { (a, b) -> assertThat(b.from).isEqualTo(a.to) }
        assertThat(shares.last().to).isEqualTo(1.0)
        shares.forEach { assertThat(it.from).isLessThan(it.to) }
    }

    @Test
    fun `the live set is read once a second however often it is asked`() {
        liveDevices(device("member:1", "a", 1_000))

        service.share("member:1", "a")
        service.share("member:2", "b")
        verify(exactly = 1) { live.devices() }

        clock.now = start.plusSeconds(1)
        service.share("member:1", "a")
        verify(exactly = 2) { live.devices() }
    }

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }
}
