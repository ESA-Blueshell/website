package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.LeaderboardService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class LeaderboardControllerTest {
    private val leaderboard = mockk<LeaderboardService>()
    private val stream = mockk<LeaderboardStream>()
    private val controller = LeaderboardController(leaderboard, stream)

    @Test
    fun `the board maps the composed snapshot`() {
        every { leaderboard.snapshot() } returns Leaderboard(house = null, members = emptyList())

        assertThat(controller.board())
            .isEqualTo(LeaderboardResponse(house = null, members = emptyList(), fastest = emptyList(), record = null, combinedPps = 0))
    }

    @Test
    fun `the stream endpoint opens a live stream`() {
        val emitter = SseEmitter()
        every { stream.open() } returns emitter

        assertThat(controller.stream()).isSameAs(emitter)
    }
}
