package net.blueshell.api.pinger.web

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PingerPaintService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.Instant
import java.util.function.Consumer

class PaintStreamTest {
    private val paint = mockk<PingerPaintService>()
    private val json = JsonMapper.builder().build()
    private val start = Instant.parse("2026-10-09T20:00:00Z")

    private fun job(
        rate: Int,
        at: Instant = start,
    ) = PaintView("2001:db8::/64", rate, true, emptyList(), at)

    private fun stream(
        maxStreams: Int = 100,
        maxPerClient: Int = 100,
    ): PaintStream = spyk(PaintStream(paint, json, 0, maxStreams, maxPerClient))

    private fun emitter(): SseEmitter = mockk(relaxed = true)

    /** What each send on [emitter] wrote, as the SSE text. */
    private fun sent(emitter: SseEmitter): List<String> {
        val events = mutableListOf<SseEmitter.SseEventBuilder>()
        verify(atLeast = 0) { emitter.send(capture(events)) }
        return events.map { event -> event.build().joinToString("") { it.data.toString() } }
    }

    private fun PaintStream.opening(
        emitter: SseEmitter,
        client: String = "192.0.2.1",
    ): SseEmitter {
        every { newEmitter() } returns emitter
        return open(client)
    }

    @Test
    fun `the default emitter never times out`() {
        every { paint.current() } returns job(1)

        assertThat(PaintStream(paint, json, 0, 10, 10).open("192.0.2.1").timeout).isEqualTo(0)
    }

    @Test
    fun `a refresh with no open stream reads nothing`() {
        stream().refresh()

        verify(exactly = 0) { paint.current() }
    }

    @Test
    fun `opening a stream primes it with the current paint job as JSON`() {
        every { paint.current() } returns job(10)
        val emitter = emitter()

        stream().opening(emitter)

        val first = sent(emitter).single()
        assertThat(first).startsWith("data:{")
        assertThat(first).contains("\"ratePps\":10").contains("\"serverTime\":\"2026-10-09T20:00:00Z\"")
    }

    @Test
    fun `a refresh pushes a changed job and skips one where only the clock moved`() {
        every { paint.current() } returnsMany listOf(job(10), job(20), job(20, start.plusSeconds(1)))
        val emitter = emitter()
        val stream = stream()
        stream.opening(emitter)

        stream.refresh()
        stream.refresh()

        assertThat(sent(emitter)).hasSize(2)
        assertThat(sent(emitter)[1]).contains("\"ratePps\":20")
    }

    @Test
    fun `a heartbeat sends a bare comment`() {
        every { paint.current() } returns job(10)
        val emitter = emitter()
        val stream = stream()
        stream.opening(emitter)

        stream.heartbeat()

        assertThat(sent(emitter)[1]).isEqualTo(":\n\n")
    }

    @Test
    fun `past the global cap a stream is refused with 503`() {
        every { paint.current() } returns job(1)
        val stream = stream(maxStreams = 2)
        stream.opening(emitter(), "192.0.2.1")
        stream.opening(emitter(), "192.0.2.2")

        assertThatThrownBy { stream.opening(emitter(), "192.0.2.3") }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            }
    }

    @Test
    fun `past the per-client cap a stream is refused with 429, and other clients still open`() {
        every { paint.current() } returns job(1)
        val stream = stream(maxStreams = 3, maxPerClient = 2)
        stream.opening(emitter(), "192.0.2.1")
        stream.opening(emitter(), "192.0.2.1")

        assertThatThrownBy { stream.opening(emitter(), "192.0.2.1") }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            }
        // The refused open gave its global slot back, so the third slot is free for someone else.
        stream.opening(emitter(), "192.0.2.2")
    }

    @Test
    fun `a failed send ends the stream and frees its slot`() {
        every { paint.current() } returns job(10)
        val stream = stream(maxStreams = 1)
        val broken = emitter()
        stream.opening(broken)
        every { broken.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("reset")

        stream.heartbeat()

        verify { broken.completeWithError(any()) }
        stream.opening(emitter())
    }

    @Test
    fun `a send on a completed emitter frees its slot`() {
        every { paint.current() } returns job(10)
        val stream = stream(maxStreams = 1)
        val done = emitter()
        stream.opening(done)
        every { done.send(any<SseEmitter.SseEventBuilder>()) } throws IllegalStateException("completed")

        stream.heartbeat()

        stream.opening(emitter())
    }

    @Test
    fun `a stream that cannot be primed frees its slot`() {
        every { paint.current() } throws IllegalStateException("database down")
        val stream = stream(maxStreams = 1, maxPerClient = 1)

        assertThatThrownBy { stream.opening(emitter()) }.isInstanceOf(IllegalStateException::class.java)

        every { paint.current() } returns job(1)
        stream.opening(emitter())
    }

    @Test
    fun `completion, timeout and error each free the slot exactly once`() {
        every { paint.current() } returns job(10)
        val stream = stream(maxStreams = 2, maxPerClient = 2)
        val completed = slot<Runnable>()
        val timedOut = slot<Runnable>()
        val failed = slot<Consumer<Throwable>>()
        val ending = emitter()
        every { ending.onCompletion(capture(completed)) } just Runs
        every { ending.onTimeout(capture(timedOut)) } just Runs
        every { ending.onError(capture(failed)) } just Runs
        stream.opening(ending)

        // An error is followed by completion, and a timeout may be too: one slot back, not three.
        failed.captured.accept(IOException("reset"))
        timedOut.captured.run()
        completed.captured.run()

        stream.opening(emitter())
        stream.opening(emitter())
        assertThatThrownBy { stream.opening(emitter()) }.isInstanceOf(ResponseStatusException::class.java)
        // Freed: a changed job reaches only the two live streams.
        every { paint.current() } returns job(99)
        stream.refresh()
        assertThat(sent(ending)).hasSize(1)
    }
}
