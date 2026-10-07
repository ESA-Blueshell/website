package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.LeaderboardOptIns
import net.blueshell.api.pinger.api.LeaderboardService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class LeaderboardControllerTest {
    private val leaderboard = mockk<LeaderboardService>()
    private val optIns = mockk<LeaderboardOptIns>()
    private val stream = mockk<LeaderboardStream>()
    private val currentUser = mockk<CurrentUserProvider>()
    private val controller = LeaderboardController(leaderboard, optIns, stream, currentUser)

    private val member = CurrentUser(id = 7, roles = setOf(Role.MEMBER), addressId = null)

    @Test
    fun `the board maps the composed snapshot`() {
        every { leaderboard.snapshot() } returns Leaderboard(house = null, members = emptyList())

        assertThat(controller.board()).isEqualTo(LeaderboardResponse(house = null, members = emptyList()))
    }

    @Test
    fun `the stream endpoint opens a live stream`() {
        val emitter = SseEmitter()
        every { stream.open() } returns emitter

        assertThat(controller.stream()).isSameAs(emitter)
    }

    @Test
    fun `a member reads their own opt-in`() {
        every { currentUser.currentUser() } returns member
        every { optIns.isOptedIn(7) } returns true

        assertThat(controller.optIn()).isEqualTo(OptInResponse(optedIn = true))
    }

    @Test
    fun `a member sets their own opt-in`() {
        every { currentUser.currentUser() } returns member
        every { optIns.setOptedIn(7, false) } returns Unit

        assertThat(controller.setOptIn(OptInRequest(optedIn = false))).isEqualTo(OptInResponse(optedIn = false))
        verify { optIns.setOptedIn(7, false) }
    }

    @Test
    fun `a request with no member is unauthorized`() {
        every { currentUser.currentUser() } returns null

        assertThatThrownBy { controller.optIn() }.isInstanceOf(ResponseStatusException::class.java)
    }
}
