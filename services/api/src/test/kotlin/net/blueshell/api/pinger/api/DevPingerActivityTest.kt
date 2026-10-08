package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.junit.jupiter.api.Test

class DevPingerActivityTest {
    private val reports = mockk<PingerReportService>(relaxUnitFun = true)
    private val users = mockk<UserService>()
    private val activity = DevPingerActivity(reports, users)

    @Test
    fun `a tick builds the demo fleet and reports every device, the member ones too`() {
        every { users.findByUsername(any()) } answers { Entities.user(id = 1) }

        activity.tick()

        verify { reports.report(PingerIdentity.Sitecie, "sitecie-replica-a", true, any(), any()) }
        verify { reports.report(PingerIdentity.Member(1), "paid-laptop", true, any(), any()) }
    }

    @Test
    fun `a member whose account is not seeded is skipped, SiteCie still reports`() {
        every { users.findByUsername(any()) } throws IllegalStateException("no such account")

        activity.tick()

        verify(exactly = 2) { reports.report(PingerIdentity.Sitecie, any(), true, any(), any()) }
        verify(exactly = 0) { reports.report(match { it is PingerIdentity.Member }, any(), any(), any(), any()) }
    }
}
