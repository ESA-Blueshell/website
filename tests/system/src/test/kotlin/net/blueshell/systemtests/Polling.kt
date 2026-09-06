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

/*
 * When a response wait earns its place.
 *
 * `page.waitForResponse` around a click earns it when the response is the evidence — a
 * header only it carries, a body the page never renders — or when something touches the
 * page before the assertion does: a navigation, another pane opened, a second form filled.
 * There the wait is what stops the request racing whatever comes next.
 *
 * It does not earn it when the assertion is the very next thing: a `pollFor` on the stored
 * row, a Playwright assertion on what the page now shows. The wait is then a weaker second
 * copy of that assertion, and it is the copy that fails — it caps the round trip at this
 * budget, on the browser's view of a request whose success often re-renders the element the
 * click was on. #1042 and #1125 were both this. Click, then assert what the click was for.
 */

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
 *
 * This is the only evidence that belongs to *this* test: the log is cleared per
 * test and fed by that test's own page. The api container log dumped by CI on
 * failure is stack-wide, so a refusal in it may answer to another test entirely.
 * `failed=[]` means this browser was refused nothing, whatever that log holds.
 */
private fun whatTheBrowserDid(): String {
    val failures = HttpFailureLog.recent()
    val requests = HttpFailureLog.recentRequests(limit = 25)
    if (failures.isEmpty() && requests.isEmpty()) return ""
    return ". failed=$failures requests=$requests"
}
