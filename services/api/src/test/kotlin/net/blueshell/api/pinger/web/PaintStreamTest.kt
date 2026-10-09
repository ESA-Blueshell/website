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
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant

class PaintStreamTest {
    private val paint = mockk<PingerPaintService>()
    private val start = Instant.parse("2026-10-09T20:00:00Z")

    private fun job(
        rate: Int,
        at: Instant = start,
    ) = PaintView("2001:db8::/64", rate, true, emptyList(), at)

    @Test
    fun `the default emitter never times out`() {
        every { paint.current() } returns job(1)
        val stream = PaintStream(paint, 0)

        assertThat(stream.open().timeout).isEqualTo(0)
    }

    @Test
    fun `a refresh with no open stream reads nothing`() {
        val stream = PaintStream(paint, 0)

        stream.refresh()

        verify(exactly = 0) { paint.current() }
    }

    @Test
    fun `opening a stream primes it with the current paint job`() {
        every { paint.current() } returns job(10)
        val emitter = mockk<SseEmitter>(relaxed = true)

        streamEmitting(emitter).open()

        verify { emitter.send(PaintResponse.from(job(10))) }
    }

    @Test
    fun `a refresh pushes a changed job and skips one where only the clock moved`() {
        every { paint.current() } returnsMany listOf(job(10), job(20), job(20, start.plusSeconds(1)))
        val emitter = mockk<SseEmitter>(relaxed = true)
        val stream = streamEmitting(emitter)
        stream.open()

        stream.refresh()
        stream.refresh()

        verify(exactly = 1) { emitter.send(PaintResponse.from(job(20))) }
        verify(exactly = 2) { emitter.send(any<Any>()) }
    }

    @Test
    fun `a heartbeat sends a comment to every stream`() {
        every { paint.current() } returns job(10)
        val emitter = mockk<SseEmitter>(relaxed = true)
        streamEmitting(emitter).apply { open() }.heartbeat()

        verify { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `a failed send ends and drops the stream`() {
        every { paint.current() } returns job(10)
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<Any>()) } throws IOException("closed")
        val stream = streamEmitting(emitter)

        stream.open()

        verify { emitter.completeWithError(any()) }
        every { paint.current() } returns job(99)
        stream.refresh()
        verify(exactly = 1) { emitter.send(any<Any>()) }
    }

    @Test
    fun `a send on a completed emitter drops the stream`() {
        every { paint.current() } returns job(10)
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws IllegalStateException("completed")
        val stream = streamEmitting(emitter)
        stream.open()

        stream.heartbeat()
        stream.heartbeat()

        verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test
    fun `completion, timeout and error each drop the stream`() {
        listOf<(SseEmitter, io.mockk.CapturingSlot<Runnable>) -> Unit>(
            { e, s -> every { e.onCompletion(capture(s)) } just Runs },
            { e, s -> every { e.onTimeout(capture(s)) } just Runs },
        ).forEach { hook ->
            every { paint.current() } returns job(10)
            val captured = slot<Runnable>()
            val emitter = mockk<SseEmitter>(relaxed = true)
            hook(emitter, captured)
            val stream = streamEmitting(emitter)
            stream.open()

            captured.captured.run()

            every { paint.current() } returns job(99)
            stream.refresh()
            verify(exactly = 1) { emitter.send(any<Any>()) }
        }
        every { paint.current() } returns job(10)
        val failed = slot<java.util.function.Consumer<Throwable>>()
        val emitter = mockk<SseEmitter>(relaxed = true)
        every { emitter.onError(capture(failed)) } just Runs
        val stream = streamEmitting(emitter)
        stream.open()

        failed.captured.accept(IOException("reset"))

        every { paint.current() } returns job(99)
        stream.refresh()
        verify(exactly = 1) { emitter.send(any<Any>()) }
    }

    @Test
    fun `opening past the cap is refused rather than exhausting emitters`() {
        every { paint.current() } returns job(1)
        val stream = spyk(PaintStream(paint, 0))
        every { stream.newEmitter() } answers { mockk(relaxed = true) }
        repeat(2000) { stream.open() }

        assertThatThrownBy { stream.open() }.isInstanceOf(ResponseStatusException::class.java)
    }

    private fun streamEmitting(emitter: SseEmitter): PaintStream {
        val stream = spyk(PaintStream(paint, 0))
        every { stream.newEmitter() } returns emitter
        return stream
    }
}
