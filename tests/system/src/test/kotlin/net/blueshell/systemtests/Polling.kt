package net.blueshell.systemtests

/**
 * Budget every wait in the system tests shares, browser-driven or not.
 *
 * Five seconds is a ceiling, not a target: a step that needs longer is
 * waiting on the wrong thing — a sleep standing in for a signal, an
 * assertion racing a request the test never awaited, a locator resolved
 * before the page had the data. Raising it hides that instead of fixing it.
 */
const val POLL_TIMEOUT_MS: Long = 5_000

private const val POLL_INTERVAL_MS: Long = 100

/** Polls `predicate` until it holds, or fails once the budget is spent. */
fun pollFor(description: String, timeoutMs: Long = POLL_TIMEOUT_MS, predicate: () -> Boolean) {
    pollForValue(description, timeoutMs) { if (predicate()) Unit else null }
}

/** Polls `producer` until it yields a value, or fails once the budget is spent. */
fun <T : Any> pollForValue(
    description: String,
    timeoutMs: Long = POLL_TIMEOUT_MS,
    producer: () -> T?,
): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        producer()?.let { return it }
        if (System.currentTimeMillis() >= deadline) break
        Thread.sleep(POLL_INTERVAL_MS)
    }
    throw AssertionError("Expected $description within ${timeoutMs}ms")
}
