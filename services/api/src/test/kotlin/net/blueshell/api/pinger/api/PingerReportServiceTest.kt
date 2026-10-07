package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PingerReportServiceTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val contributions = mockk<PingerContributionRepository>()
    private val live = mockk<PingerLiveStore>(relaxUnitFun = true)
    private val service = PingerReportService(contributions, live, Clock.fixed(now, ZoneOffset.UTC))

    private val member = PingerIdentity.Member(42)

    private fun existing(
        total: Long,
        lastSessionSent: Long,
    ) = PingerContribution(
        identity = member.key,
        memberId = member.memberId,
        updated = Instant.EPOCH,
        totalSent = total,
        lastSessionSent = lastSessionSent,
    )

    @Test
    fun `the first report opens the row and accrues the full count`() {
        every { contributions.lockByIdentity(member.key) } returns null
        val saved = slot<PingerContribution>()
        every { contributions.save(capture(saved)) } answers { saved.captured }

        service.report(member, online = true, pps = 128, sent = 500)

        assertThat(saved.captured.identity).isEqualTo("member:42")
        assertThat(saved.captured.memberId).isEqualTo(42)
        assertThat(saved.captured.totalSent).isEqualTo(500)
        assertThat(saved.captured.lastSessionSent).isEqualTo(500)
        assertThat(saved.captured.updated).isEqualTo(now)
        verify { live.touch("member:42", true, 128, now) }
    }

    @Test
    fun `a higher counter adds only the delta since the last report`() {
        every { contributions.lockByIdentity(member.key) } returns existing(total = 500, lastSessionSent = 500)
        val saved = slot<PingerContribution>()
        every { contributions.save(capture(saved)) } answers { saved.captured }

        service.report(member, online = true, pps = 64, sent = 800)

        assertThat(saved.captured.totalSent).isEqualTo(800)
        assertThat(saved.captured.lastSessionSent).isEqualTo(800)
    }

    @Test
    fun `an equal counter is a duplicate report and adds nothing`() {
        every { contributions.lockByIdentity(member.key) } returns existing(total = 800, lastSessionSent = 800)
        val saved = slot<PingerContribution>()
        every { contributions.save(capture(saved)) } answers { saved.captured }

        service.report(member, online = true, pps = 64, sent = 800)

        assertThat(saved.captured.totalSent).isEqualTo(800)
    }

    @Test
    fun `a lower counter is a restarted session and adds the full count`() {
        every { contributions.lockByIdentity(member.key) } returns existing(total = 1_000, lastSessionSent = 1_000)
        val saved = slot<PingerContribution>()
        every { contributions.save(capture(saved)) } answers { saved.captured }

        service.report(member, online = true, pps = 64, sent = 50)

        assertThat(saved.captured.totalSent).isEqualTo(1_050)
        assertThat(saved.captured.lastSessionSent).isEqualTo(50)
    }

    @Test
    fun `a SiteCie report accrues under SiteCie with no member`() {
        every { contributions.lockByIdentity(PingerIdentity.Sitecie.key) } returns null
        val saved = slot<PingerContribution>()
        every { contributions.save(capture(saved)) } answers { saved.captured }

        service.report(PingerIdentity.Sitecie, online = false, pps = 0, sent = 10)

        assertThat(saved.captured.identity).isEqualTo("sitecie")
        assertThat(saved.captured.memberId).isNull()
        assertThat(saved.captured.totalSent).isEqualTo(10)
        verify { live.touch("sitecie", false, 0, now) }
    }
}
