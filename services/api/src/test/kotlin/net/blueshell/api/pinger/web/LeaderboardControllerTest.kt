package net.blueshell.api.pinger.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.LeaderboardService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LeaderboardControllerTest {
    private val leaderboard = mockk<LeaderboardService>()
    private val controller = LeaderboardController(leaderboard)

    @Test
    fun `the board maps the composed snapshot`() {
        every { leaderboard.snapshot() } returns Leaderboard(house = null, members = emptyList())

        assertThat(controller.board())
            .isEqualTo(LeaderboardResponse(house = null, members = emptyList(), fastest = emptyList(), record = null))
    }
}
