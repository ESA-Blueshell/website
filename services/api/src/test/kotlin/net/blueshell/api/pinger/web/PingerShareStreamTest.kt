package net.blueshell.api.pinger.web

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import net.blueshell.api.pinger.api.PingerShare
import net.blueshell.api.pinger.api.PingerShareService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.function.Consumer

class PingerShareStreamTest {
    private val shares = mockk<PingerShareService>()
    private val timeoutMs = 1_000L

    private fun share(
        from: Double,
        to: Double = 1.0,
        devices: Int = 2,
    ) = PingerShare(from, to, devices)

    @Test
    fun `the default emitter carries the configured timeout`() {
        every { shares.share(any(), any()) } returns share(0.0)
        val stream = PingerShareStream(shares, timeoutMs)

        val emitter = stream.open("member:1", "a")

        assertThat(emitter.timeout).isEqualTo(timeoutMs)
    }

    @Test
    fun `opening a stream primes it with the device's current share`() {
        every { shares.share("member:1", "a") } returns share(0.5)
        val emitter = mockk<SseEmitter>(relaxed = true)

        streamEmitting(emitter).open("member:1", "a")

        verify { emitter.send(PingerShareResponse(from = 0.5, to = 1.0, devices = 2)) }
    }

    @Test
    fun `a tick pushes a changed share and skips one that moved by less than the tolerance`() {
        every { shares.share("member:1", "a") } returnsMany
            listOf(share(0.5), share(0.5 + 1e-9), share(0.25), share(0.25, devices = 3))
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)
        stream.open("member:1", "a")

        stream.tick()
        stream.tick()
        stream.tick()

        verify(exactly = 1) { emitter.send(PingerShareResponse(0.5, 1.0, 2)) }
        verify(exactly = 1) { emitter.send(PingerShareResponse(0.25, 1.0, 2)) }
        verify(exactly = 1) { emitter.send(PingerShareResponse(0.25, 1.0, 3)) }
    }

    @Test
    fun `a change in the slice's end alone is pushed`() {
        every { shares.share("member:1", "a") } returnsMany listOf(share(0.0, 0.5), share(0.0, 0.75))
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)
        stream.open("member:1", "a")

        stream.tick()

        verify(exactly = 1) { emitter.send(PingerShareResponse(0.0, 0.75, 2)) }
    }

    @Test
    fun `each stream is sent its own device's share`() {
        every { shares.share("member:1", "a") } returns share(0.0, 0.5)
        every { shares.share("member:2", "b") } returns share(0.5, 1.0)
        val a = mockk<SseEmitter>(relaxed = true)
        val b = mockk<SseEmitter>(relaxed = true)
        val stream = spyk(PingerShareStream(shares, timeoutMs))
        every { stream.newEmitter() } returnsMany listOf(a, b)

        stream.open("member:1", "a")
        stream.open("member:2", "b")

        verify { a.send(PingerShareResponse(0.0, 0.5, 2)) }
        verify { b.send(PingerShareResponse(0.5, 1.0, 2)) }
    }

    @Test
    fun `a tick with no open stream reads nothing`() {
        PingerShareStream(shares, timeoutMs).tick()

        verify(exactly = 0) { shares.share(any(), any()) }
    }

    @Test
    fun `a heartbeat sends a comment to every open stream`() {
        every { shares.share(any(), any()) } returns share(0.0)
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)
        stream.open("member:1", "a")

        stream.heartbeat()

        verify { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `a failed heartbeat ends and drops the stream`() {
        every { shares.share(any(), any()) } returnsMany listOf(share(0.0), share(0.5))
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("closed")
        val stream = streamEmitting(emitter)
        stream.open("member:1", "a")

        stream.heartbeat()

        verify { emitter.completeWithError(any()) }
        stream.tick()
        verify(exactly = 1) { shares.share(any(), any()) }
    }

    @Test
    fun `a failed send ends and drops the stream`() {
        every { shares.share(any(), any()) } returns share(0.0)
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<Any>()) } throws IOException("closed")
        val stream = streamEmitting(emitter)

        stream.open("member:1", "a")

        verify { emitter.completeWithError(any()) }
        stream.tick()
        verify(exactly = 1) { shares.share(any(), any()) }
    }

    @Test
    fun `completion drops the stream`() {
        val completion = slot<Runnable>()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.onCompletion(capture(completion)) } just Runs

        assertDroppedAfter(emitter) { completion.captured.run() }
    }

    @Test
    fun `a timeout drops the stream`() {
        val timeout = slot<Runnable>()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.onTimeout(capture(timeout)) } just Runs

        assertDroppedAfter(emitter) { timeout.captured.run() }
    }

    @Test
    fun `an error drops the stream`() {
        val error = slot<Consumer<Throwable>>()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.onError(capture(error)) } just Runs

        assertDroppedAfter(emitter) { error.captured.accept(IOException("reset")) }
    }

    private fun assertDroppedAfter(
        emitter: SseEmitter,
        end: () -> Unit,
    ) {
        every { shares.share(any(), any()) } returns share(0.0)
        val stream = streamEmitting(emitter)
        stream.open("member:1", "a")

        end()

        // Dropped: a later tick reads no share for it at all.
        stream.tick()
        verify(exactly = 1) { shares.share(any(), any()) }
    }

    @Test
    fun `opening past the cap is refused rather than exhausting emitters`() {
        every { shares.share(any(), any()) } returns share(0.0)
        val stream = spyk(PingerShareStream(shares, timeoutMs))
        every { stream.newEmitter() } answers { mockk(relaxed = true) }
        repeat(2_000) { stream.open("member:$it", "d") }

        assertThatThrownBy { stream.open("member:x", "d") }.isInstanceOf(ResponseStatusException::class.java)
    }

    private fun streamEmitting(emitter: SseEmitter): PingerShareStream {
        val stream = spyk(PingerShareStream(shares, timeoutMs))
        every { stream.newEmitter() } returns emitter
        return stream
    }
}
