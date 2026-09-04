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
    throw AssertionError("Expected $description within ${timeoutMs}ms${whatTheBrowserDid()}")
}

/**
 * What the browser did, for a poll that gave up.
 *
 * A timeout on its own says only that something never arrived. Whether the
 * request behind it was refused before it was sent, answered with a 4xx, or
 * answered fine and the assertion is reading the wrong thing are three
 * different bugs, and the suite already records enough to tell them apart.
 * Empty for the polls that never drove a browser.
 */
private fun whatTheBrowserDid(): String {
    val failures = HttpFailureLog.recent()
    val requests = HttpFailureLog.recentRequests(limit = 25)
    if (failures.isEmpty() && requests.isEmpty()) return ""
    return ". failed=$failures requests=$requests"
}
