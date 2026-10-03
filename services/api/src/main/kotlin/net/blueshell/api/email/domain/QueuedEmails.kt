package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailComposer
import net.blueshell.api.shared.job.JobQueued
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Records an email as queued the moment the job that sends it is queued, in the same transaction.
 * A payload that composes nothing, or cannot be composed yet, is left for the send to record.
 */
@Component
class QueuedEmails(
    composers: List<EmailComposer>,
    private val emails: EmailService,
) {
    private val byJobType = composers.associateBy { it.jobType }

    @EventListener
    fun on(queued: JobQueued) {
        val composer = byJobType[queued.jobType] ?: return
        val payload = queued.payload ?: return
        // The queue must not fail over the log: an email that cannot be composed now is recorded when it is sent.
        val content =
            runCatching { composer.composeQueued(payload) }
                .onFailure { log.warn("Could not compose the queued {} for job {}: {}", queued.jobType, queued.executionId, it.message) }
                .getOrNull() ?: return
        emails.recordQueued(content, composer.emailType, queued.executionId, queued.actor)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(QueuedEmails::class.java)
    }
}
