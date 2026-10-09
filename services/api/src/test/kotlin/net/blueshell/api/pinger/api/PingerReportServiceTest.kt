package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerDeviceSession
import net.blueshell.api.pinger.persistence.PingerDeviceSessionRepository
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PingerReportServiceTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val contributions = mockk<PingerContributionRepository>()
    private val devices = mockk<PingerDeviceSessionRepository>()
    private val live = mockk<PingerLiveStore>(relaxUnitFun = true)
    private val service = PingerReportService(contributions, devices, live, Clock.fixed(now, ZoneOffset.UTC))

    private val member = PingerIdentity.Member(42)

    // Stateful fakes: the one contribution row and the per-device rows persist across reports, so a
    // sequence of reports accrues exactly as it would against a database.
    private val contributionRows = HashMap<String, PingerContribution>()
    private val deviceRows = HashMap<Pair<String, String>, PingerDeviceSession>()

    // The live presence per (identity, device), so a peak reads the identity's rate across devices.
    private val liveRows = HashMap<Pair<String, String>, PingerLive>()

    init {
        every { live.touch(any(), any(), any(), any(), any()) } answers {
            liveRows[firstArg<String>() to secondArg<String>()] = PingerLive(thirdArg(), arg(3), arg(4))
        }
        every { live.aggregate(any()) } answers {
            val rows = liveRows.filterKeys { it.first == firstArg<String>() }.values
            if (rows.isEmpty()) {
                null
            } else {
                PingerLive(rows.any { it.online }, rows.filter { it.online }.sumOf { it.pps }, rows.maxOf { it.lastSeen })
            }
        }
        every { contributions.lockByIdentity(any()) } answers { contributionRows[firstArg()] }
        every { contributions.save(any()) } answers {
            val row = firstArg<PingerContribution>()
            contributionRows[row.identity] = row
            row
        }
        every { devices.lockByIdentityAndDevice(any(), any()) } answers {
            deviceRows[firstArg<String>() to secondArg<String>()]
        }
        every { devices.save(any()) } answers {
            val row = firstArg<PingerDeviceSession>()
            deviceRows[row.identity to row.deviceId] = row
            row
        }
        every { devices.countByIdentity(any()) } answers {
            val id = firstArg<String>()
            deviceRows.keys.count { it.first == id }.toLong()
        }
        every { devices.findFirstByIdentityOrderByUpdatedAsc(any()) } answers {
            val id = firstArg<String>()
            deviceRows.values.filter { it.identity == id }.minByOrNull { it.updated }
        }
        every { devices.delete(any()) } answers {
            val row = firstArg<PingerDeviceSession>()
            deviceRows.remove(row.identity to row.deviceId)
        }
    }

    private fun totalFor(identity: PingerIdentity) = contributionRows.getValue(identity.key).totalSent

    private fun deviceCounter(
        identity: PingerIdentity,
        deviceId: String,
    ) = deviceRows.getValue(identity.key to deviceId).lastSessionSent

    @Test
    fun `the first report opens the rows and accrues the full count`() {
        service.report(member, "laptop", online = true, pps = 128, sent = 500)

        val row = contributionRows.getValue("member:42")
        assertThat(row.memberId).isEqualTo(42)
        assertThat(row.totalSent).isEqualTo(500)
        assertThat(row.updated).isEqualTo(now)
        assertThat(deviceCounter(member, "laptop")).isEqualTo(500)
        verify { live.touch("member:42", "laptop", true, 128, now) }
    }

    @Test
    fun `a higher counter adds only the delta since that device's last report`() {
        service.report(member, "laptop", online = true, pps = 128, sent = 500)

        service.report(member, "laptop", online = true, pps = 64, sent = 800)

        assertThat(totalFor(member)).isEqualTo(800)
        assertThat(deviceCounter(member, "laptop")).isEqualTo(800)
    }

    @Test
    fun `an equal counter is a duplicate report and adds nothing`() {
        service.report(member, "laptop", online = true, pps = 64, sent = 800)

        service.report(member, "laptop", online = true, pps = 64, sent = 800)

        assertThat(totalFor(member)).isEqualTo(800)
    }

    @Test
    fun `a lower counter is that device's restarted session and adds its full count`() {
        service.report(member, "laptop", online = true, pps = 64, sent = 1_000)

        service.report(member, "laptop", online = true, pps = 64, sent = 50)

        assertThat(totalFor(member)).isEqualTo(1_050)
        assertThat(deviceCounter(member, "laptop")).isEqualTo(50)
    }

    @Test
    fun `two devices of one member each accrue into the single total without clobbering`() {
        service.report(member, "laptop", online = true, pps = 100, sent = 100) // +100 -> 100
        service.report(member, "phone", online = true, pps = 50, sent = 50) // +50 -> 150
        service.report(member, "laptop", online = true, pps = 100, sent = 300) // +200 -> 350
        service.report(member, "phone", online = true, pps = 50, sent = 50) // dup -> 350

        assertThat(totalFor(member)).isEqualTo(350)
    }

    @Test
    fun `a reset on one device starts that device fresh without disturbing the other`() {
        service.report(member, "laptop", online = true, pps = 100, sent = 300) // +300 -> 300
        service.report(member, "phone", online = true, pps = 50, sent = 200) // +200 -> 500
        service.report(member, "laptop", online = true, pps = 100, sent = 10) // laptop restart: +10 -> 510
        service.report(member, "phone", online = true, pps = 50, sent = 500) // +300 -> 810

        // laptop counted 300 + 10, phone counted 200 + 300: the exact sum, each device once.
        assertThat(totalFor(member)).isEqualTo(810)
        assertThat(deviceCounter(member, "laptop")).isEqualTo(10)
        assertThat(deviceCounter(member, "phone")).isEqualTo(500)
    }

    @Test
    fun `a SiteCie report accrues under SiteCie with no member, per replica`() {
        service.report(PingerIdentity.Sitecie, "replica-a", online = true, pps = 9_000, sent = 9_000)
        service.report(PingerIdentity.Sitecie, "replica-b", online = true, pps = 8_000, sent = 8_000)

        val row = contributionRows.getValue("sitecie")
        assertThat(row.memberId).isNull()
        assertThat(row.totalSent).isEqualTo(17_000)
        verify { live.touch("sitecie", "replica-a", true, 9_000, now) }
        verify { live.touch("sitecie", "replica-b", true, 8_000, now) }
    }

    @Test
    fun `a sender at the app's top rate counts in full`() {
        service.report(member, "laptop", online = true, pps = 2_192_982, sent = 21_929_820)

        assertThat(totalFor(member)).isEqualTo(21_929_820)
    }

    @Test
    fun `one report cannot move the total by more than the rate cap over a minute`() {
        service.report(member, "laptop", online = true, pps = 1, sent = 2_000_000_000)

        assertThat(totalFor(member)).isEqualTo(MAX_REPORT_PPS * 60)
    }

    @Test
    fun `reports missed during a stall still count once the device reports again`() {
        deviceRows[member.key to "laptop"] =
            PingerDeviceSession(identity = member.key, deviceId = "laptop", updated = now.minusSeconds(600))
        val fiveMinutesAtTheCap = MAX_REPORT_PPS * 300

        service.report(member, "laptop", online = true, pps = 1, sent = fiveMinutesAtTheCap)

        assertThat(totalFor(member)).isEqualTo(fiveMinutesAtTheCap)
    }

    @Test
    fun `a new device past the cap evicts the identity's oldest rather than growing without bound`() {
        repeat(10) { i -> service.report(member, "device-$i", online = true, pps = 1, sent = 1) }
        assertThat(deviceRows.keys.count { it.first == member.key }).isEqualTo(10)

        service.report(member, "device-10", online = true, pps = 1, sent = 1)

        assertThat(deviceRows.keys.count { it.first == member.key }).isEqualTo(10)
    }

    @Test
    fun `an online report sets the identity's peak and when it was set`() {
        service.report(member, "laptop", online = true, pps = 128, sent = 500)

        val row = contributionRows.getValue(member.key)
        assertThat(row.peakPps).isEqualTo(128)
        assertThat(row.peakAt).isEqualTo(now)
    }

    @Test
    fun `the peak only rises, so a slower report keeps the earlier top rate`() {
        service.report(member, "laptop", online = true, pps = 500, sent = 500)

        service.report(member, "laptop", online = true, pps = 100, sent = 600)

        assertThat(contributionRows.getValue(member.key).peakPps).isEqualTo(500)
    }

    @Test
    fun `the peak sums the member's online devices at the moment of the report`() {
        service.report(member, "laptop", online = true, pps = 170, sent = 100)

        service.report(member, "phone", online = true, pps = 130, sent = 100)

        assertThat(contributionRows.getValue(member.key).peakPps).isEqualTo(300)
    }

    @Test
    fun `an offline report sets no peak`() {
        service.report(member, "laptop", online = false, pps = 900, sent = 100)

        val row = contributionRows.getValue(member.key)
        assertThat(row.peakPps).isZero()
        assertThat(row.peakAt).isNull()
    }

    @Test
    fun `SiteCie keeps its own peak across its replicas`() {
        service.report(PingerIdentity.Sitecie, "replica-a", online = true, pps = 9_000, sent = 9_000)
        service.report(PingerIdentity.Sitecie, "replica-b", online = true, pps = 8_000, sent = 8_000)

        assertThat(contributionRows.getValue("sitecie").peakPps).isEqualTo(17_000)
    }
}
