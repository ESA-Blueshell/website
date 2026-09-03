package net.blueshell.acceptance

import net.blueshell.systemtests.TestHelper

/**
 * Reading a member's inbox, for steps that assert what arrived.
 *
 * Emails are queued rather than sent inline, so every assertion about one is a question
 * about a moment that has not happened yet. Both directions of that question live here so
 * they wait the same way and fail with the same detail.
 */
object Inbox {

    /** Long enough for a queued job to be picked up, rendered and delivered. */
    const val DELIVERY_TIMEOUT_MS = 15_000L

    private const val POLL_INTERVAL_MS = 250L

    /**
     * Waits for one of this member's emails to carry [subjectFragment].
     *
     * Filtered by recipient rather than by subject: the outbox matches a subject exactly,
     * and several of ours end in an academic year the scenario's period works out to.
     *
     * A write that was refused shows up here as nothing arriving, so the failure quotes what
     * the request answered — otherwise every cause reads as "no email came".
     */
    fun await(
        recipient: String,
        subjectFragment: String,
        lastStatusCode: Int? = null,
        lastResponseBody: String? = null,
    ): TestHelper.SentEmail {
        val deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS
        var seen: List<TestHelper.SentEmail> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            seen = TestHelper.findEmails(recipient = recipient)
            seen.firstOrNull { it.subject.contains(subjectFragment) }?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError(
            "No email to $recipient with a subject containing \"$subjectFragment\" " +
                "within ${DELIVERY_TIMEOUT_MS}ms. " +
                "The request answered $lastStatusCode: $lastResponseBody. " +
                "That inbox holds: ${seen.map { it.subject }}",
        )
    }

    /**
     * Asserts none of this member's emails carries [subjectFragment], and keeps asserting it
     * for [settleMs].
     *
     * Asking once would pass before a wrongly-queued email could have been delivered, which
     * makes the assertion agree with the bug. The window is shorter than [DELIVERY_TIMEOUT_MS]
     * because this one is paid in full by every run: an email that got as far as being queued
     * arrives well inside it.
     */
    fun awaitNothing(recipient: String, subjectFragment: String, settleMs: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + settleMs
        while (System.currentTimeMillis() < deadline) {
            val arrived = TestHelper.findEmails(recipient = recipient)
                .filter { it.subject.contains(subjectFragment) }
            if (arrived.isNotEmpty()) {
                throw AssertionError(
                    "$recipient should have received no \"$subjectFragment\" email, but got " +
                        "${arrived.map { it.subject }}",
                )
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }
}
