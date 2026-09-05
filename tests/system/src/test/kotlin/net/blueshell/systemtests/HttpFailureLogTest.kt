package net.blueshell.systemtests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The offsets a failed test prints are the first thing anyone reads, and they were measured
 * from the shard's first test rather than from this one — so a request that took a second
 * read as thirteen, and a search for the cause went after a machine that was never slow.
 *
 * Needs no browser and no stack.
 */
@Tag("system")
class HttpFailureLogTest {

    private fun offsetOf(entry: String): Long =
        entry.substringAfter("+").substringBefore("ms").toLong()

    @Test
    fun `a cleared log times the test that follows it, not the one before`() {
        HttpFailureLog.clear()
        Thread.sleep(SPAN_MS)
        HttpFailureLog.mark("late in the first test")
        val before = offsetOf(HttpFailureLog.recentRequests().last())

        HttpFailureLog.clear()
        HttpFailureLog.mark("early in the second test")
        val after = offsetOf(HttpFailureLog.recentRequests().last())

        assertThat(before).isGreaterThanOrEqualTo(SPAN_MS)
        assertThat(after)
            .describedAs("a mark made just after clear() is at the start of its own timeline")
            .isLessThan(SPAN_MS)
    }

    @Test
    fun `clearing drops what the previous test saw`() {
        HttpFailureLog.clear()
        HttpFailureLog.record(500, "GET", "/committees")
        HttpFailureLog.mark("submit click")

        HttpFailureLog.clear()

        assertThat(HttpFailureLog.recent()).isEmpty()
        assertThat(HttpFailureLog.recentRequests()).isEmpty()
    }

    private companion object {
        /** Long enough that a mark before it and one after it cannot be confused. */
        const val SPAN_MS = 50L
    }
}
