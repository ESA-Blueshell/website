package net.blueshell.systemtests

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.Request
import com.microsoft.playwright.Response
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * Acts on [control] once it is ready, and returns the response [matches] accepts.
 *
 * Playwright's click auto-waits for its own control, so a click written inside a
 * `page.waitForResponse` block spends the response's budget on becoming clickable. A control
 * behind a `v-if` fed by a second read can spend all of it: the wait then expires on a request
 * the browser never made, and the failure reads `failed=[]` because nothing was refused. That
 * budget is shared with whatever else the page is loading — fonts, embedded widgets, anything
 * third-party — so the margin is thinner than the test looks (#1287).
 *
 * Here the control is waited for first, on a budget of its own, and the response window opens
 * only once the page can be acted on. [act] defaults to clicking [control]; pass it when the
 * action is a fill, or when a dialog has to be confirmed before the request goes out.
 */
fun Page.awaitResponseFrom(
    control: Locator,
    expected: String,
    timeoutMs: Long = POLL_TIMEOUT_MS,
    act: () -> Unit = { control.click() },
    matches: (Response) -> Boolean,
): Response {
    try {
        assertThat(control).isEnabled(LocatorAssertions.IsEnabledOptions().setTimeout(timeoutMs.toDouble()))
    } catch (e: AssertionError) {
        throw AssertionError(
            "Expected the control for $expected to be ready within ${timeoutMs}ms${whatTheBrowserDid()}",
            e,
        )
    }
    return try {
        waitForResponse(
            Predicate { matches(it) },
            Page.WaitForResponseOptions().setTimeout(timeoutMs.toDouble()),
        ) { act() }
    } catch (e: PlaywrightException) {
        throw AssertionError("Expected $expected within ${timeoutMs}ms${whatTheBrowserDid()}", e)
    }
}

/**
 * Clicks [control] until [done] holds, or fails once the budget is spent.
 *
 * A click Playwright reports as delivered can still do nothing: a read landing while the
 * handler is being attached re-renders the control, and the event goes to the node that was
 * replaced. Nothing is refused and nothing is thrown — the request simply never leaves, and
 * the poll that follows spends its whole budget on a click that was lost (#1287).
 *
 * Retrying the click is what tells the two apart, so [done] must be the thing the click was
 * for — a row gone from the database, a panel now showing — and the click must be safe to
 * repeat. Use [awaitResponseFrom] instead when the response itself is the evidence.
 */
fun clickUntil(
    control: Locator,
    description: String,
    timeoutMs: Long = POLL_TIMEOUT_MS,
    done: () -> Boolean,
) {
    control.waitFor()
    val deadline = System.currentTimeMillis() + timeoutMs
    var clicks = 0
    var lastRefusal: Throwable? = null
    while (true) {
        if (done()) return
        // A click that worked takes the control with it — the row it belonged to is gone, or
        // the form it submitted has moved on. Gone therefore means wait, not click again.
        if (control.count() > 0) {
            runCatching { control.click() }.onFailure { lastRefusal = it }
            clicks++
            HttpFailureLog.mark("click $clicks on $description")
        }
        val slice = System.currentTimeMillis() + CLICK_RETRY_MS
        while (System.currentTimeMillis() < slice) {
            if (done()) return
            // Not Thread.sleep: a `done` fed by a page listener only hears events inside a Playwright call.
            control.page().waitForTimeout(POLL_INTERVAL_MS.toDouble())
        }
        if (System.currentTimeMillis() >= deadline) break
    }
    throw AssertionError(
        "Expected $description within ${timeoutMs}ms, over $clicks clicks${whatTheBrowserDid()}",
        lastRefusal,
    )
}

/**
 * [awaitResponseFrom] for a control whose click can be lost and whose request is safe to send
 * twice, such as the read that opens a preview.
 *
 * The click is repeated, as [clickUntil] does, until a request [sent] accepts leaves the
 * browser; the response is then awaited on what is left of the budget. A failure says how many
 * clicks the control itself received and whether it was still in the page, which tells a click
 * that missed from a control replaced under it (#1700).
 */
fun Page.awaitResponseFromRetried(
    control: Locator,
    expected: String,
    timeoutMs: Long = POLL_TIMEOUT_MS,
    sent: (Request) -> Boolean,
    matches: (Response) -> Boolean,
): Response {
    control.waitFor()
    val target = control.elementHandle()
    target.evaluate("el => { el.__clicks = 0; el.addEventListener('click', () => el.__clicks++, {capture: true}) }")
    val request = AtomicReference<Request?>()
    val response = AtomicReference<Response?>()
    val onRequest = Consumer<Request> { if (sent(it)) request.compareAndSet(null, it) }
    val onResponse = Consumer<Response> { if (matches(it)) response.compareAndSet(null, it) }
    onRequest(onRequest)
    onResponse(onResponse)
    val deadline = System.currentTimeMillis() + timeoutMs
    try {
        clickUntil(control, "the request for $expected", timeoutMs) { request.get() != null }
        // A Playwright call, not a sleep: the Java client only delivers events while it is in one.
        runCatching {
            waitForCondition(
                { response.get() != null },
                Page.WaitForConditionOptions().setTimeout(maxOf(1L, deadline - System.currentTimeMillis()).toDouble()),
            )
        }
        return response.get() ?: throw AssertionError("Expected $expected within ${timeoutMs}ms${whatTheBrowserDid()}")
    } catch (e: AssertionError) {
        val clicks = runCatching { target.evaluate("el => el.__clicks") }.getOrNull()
        val attached = runCatching { target.evaluate("el => el.isConnected") }.getOrNull()
        throw AssertionError("${e.message}; the control received $clicks clicks and is attached=$attached", e)
    } finally {
        offRequest(onRequest)
        offResponse(onResponse)
    }
}

/**
 * How long a click gets to produce what it was for before it is clicked again.
 *
 * Long enough that a click which did land is never clicked twice — the request leaves in
 * milliseconds — and short enough that a lost one is retried while budget remains.
 */
private const val CLICK_RETRY_MS: Long = 1_000
