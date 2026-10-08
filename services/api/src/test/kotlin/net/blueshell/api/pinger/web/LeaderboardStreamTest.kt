package net.blueshell.api.pinger.web

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import net.blueshell.api.pinger.api.HouseStanding
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.LeaderboardService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

class LeaderboardStreamTest {
    private val leaderboard = mockk<LeaderboardService>()
    private val timeoutMs = 1_000L

    private fun board(total: Long) = Leaderboard(house = HouseStanding("SiteCie", total, online = true, pps = 0), members = emptyList())

    @Test
    fun `the default emitter carries the configured timeout`() {
        every { leaderboard.snapshot() } returns board(1)
        val stream = LeaderboardStream(leaderboard, timeoutMs)

        val emitter = stream.open()

        assertThat(emitter.timeout).isEqualTo(timeoutMs)
    }

    @Test
    fun `a tick with no open stream reads nothing`() {
        val stream = LeaderboardStream(leaderboard, timeoutMs)

        stream.tick()

        verify(exactly = 0) { leaderboard.snapshot() }
    }

    @Test
    fun `opening a stream primes it with the current board`() {
        every { leaderboard.snapshot() } returns board(10)
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)

        stream.open()

        verify { emitter.send(LeaderboardResponse.from(board(10))) }
    }

    @Test
    fun `a tick pushes the board when it changed and skips it when it did not`() {
        every { leaderboard.snapshot() } returnsMany listOf(board(10), board(20), board(20))
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)
        stream.open()

        stream.tick()
        stream.tick()

        verify(exactly = 1) { emitter.send(LeaderboardResponse.from(board(20))) }
    }

    @Test
    fun `a failed send ends and drops the stream`() {
        every { leaderboard.snapshot() } returns board(10)
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<Any>()) } throws IOException("closed")
        val stream = streamEmitting(emitter)

        stream.open()

        verify { emitter.completeWithError(any()) }
        // Dropped: a later tick, even with a changed board, pushes nothing to it.
        every { leaderboard.snapshot() } returns board(99)
        stream.tick()
        verify(exactly = 1) { emitter.send(any<Any>()) }
    }

    @Test
    fun `completion drops the stream`() {
        every { leaderboard.snapshot() } returns board(10)
        val completion = slot<Runnable>()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.onCompletion(capture(completion)) } just Runs
        val stream = streamEmitting(emitter)
        stream.open()

        completion.captured.run()

        // Dropped: a later tick, even with a changed board, pushes nothing more to it.
        every { leaderboard.snapshot() } returns board(99)
        stream.tick()
        verify(exactly = 1) { emitter.send(any<Any>()) }
    }

    @Test
    fun `opening past the cap is refused rather than exhausting emitters`() {
        every { leaderboard.snapshot() } returns board(1)
        val stream = spyk(LeaderboardStream(leaderboard, timeoutMs))
        every { stream.newEmitter() } answers { mockk(relaxed = true) }
        repeat(500) { stream.open() }

        assertThatThrownBy { stream.open() }.isInstanceOf(ResponseStatusException::class.java)
    }

    private fun streamEmitting(emitter: SseEmitter): LeaderboardStream {
        val stream = spyk(LeaderboardStream(leaderboard, timeoutMs))
        every { stream.newEmitter() } returns emitter
        return stream
    }
}
