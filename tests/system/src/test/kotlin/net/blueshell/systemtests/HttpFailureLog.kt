package net.blueshell.systemtests

import java.util.Collections

/**
 * Every 4xx/5xx the browser saw during the current test.
 *
 * A page that redirects itself mid-test — an expired session, a guard sending
 * the browser home — leaves no trace in the DOM it navigated away from, so a
 * step that then waits for a request it will never see cannot say why. The
 * responses that preceded it can.
 */
object HttpFailureLog {
    private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun clear() {
        entries.clear()
        requests.clear()
    }

    fun record(status: Int, method: String, url: String) {
        entries += "$status $method $url"
    }

    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    fun recordRequest(method: String, url: String) {
        requests += "+${sinceStart()}ms $method $url"
    }

    /** Marks a step in the same timeline as the requests, for ordering. */
    fun mark(what: String) {
        requests += "+${sinceStart()}ms [$what]"
    }

    private val start = System.currentTimeMillis()

    private fun sinceStart(): Long = System.currentTimeMillis() - start

    fun recentRequests(limit: Int = 12): List<String> = synchronized(requests) { requests.takeLast(limit) }

    fun recent(limit: Int = 5): List<String> = synchronized(entries) { entries.takeLast(limit) }
}
