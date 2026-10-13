package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailComposer
import net.blueshell.api.shared.job.JobQueued
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Records an email as queued the moment the job that sends it is queued, in the queuing
 * transaction where there is one. A payload that composes nothing, or cannot be composed yet, is
 * left for the send to record.
 */
@Component
class QueuedEmails(
    composers: List<EmailComposer>,
    private val emails: EmailService,
) {
    private val byJobType = composers.associateBy { it.jobType }
    private val byEmailType = composers.associateBy { it.emailType }

    @EventListener
    fun on(queued: JobQueued) {
        val composer = byJobType[queued.jobType] ?: return
        val payload = queued.payload ?: return
        // The queue must not fail over the log: an email that cannot be composed or recorded now is recorded when it is sent.
        runCatching {
            val content = composer.composeQueued(payload) ?: return
            emails.recordQueued(content, composer.emailType, queued.executionId, queued.actor)
        }.onFailure { log.warn("Could not record the queued {} for job {}: {}", queued.jobType, queued.executionId, it.message) }
    }

    /**
     * Whether an email of this type may be made again. Only one a job sends by itself: run again,
     * a job that sends many would send them all.
     */
    fun resendable(emailType: String?): Boolean = byEmailType[emailType]?.resendable ?: false

    fun holdsOneTimeLink(emailType: String?): Boolean = byEmailType[emailType]?.resendable == false

    private companion object {
        private val log = LoggerFactory.getLogger(QueuedEmails::class.java)
    }
}
