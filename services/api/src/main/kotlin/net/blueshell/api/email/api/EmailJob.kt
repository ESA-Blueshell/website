package net.blueshell.api.email.api

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.JobDefinition
import tools.jackson.databind.ObjectMapper

/** A job that sends one email, which the email log records as queued before the job runs. */
interface EmailComposer {
    val jobType: String
    val emailType: String

    /** The email a queued job's payload will send, or null where it sends none. */
    fun composeQueued(payload: String): EmailContent?
}

/**
 * A job whose whole work is one email. The email is composed from the payload twice: once when
 * the job is queued, so the log shows it at once, and again when it runs, from the data as it then
 * stands.
 */
abstract class EmailJob<T : Any>(
    private val objectMapper: ObjectMapper,
    definition: JobDefinition<T>,
    private val emails: EmailSenderService,
    override val emailType: String = definition.type,
) : AbstractJsonJobHandler<T>(objectMapper, definition),
    EmailComposer {
    /** The email this payload sends, or null where it sends none. */
    protected abstract fun compose(payload: T): EmailContent?

    override fun handlePayload(payload: T) {
        val content = compose(payload) ?: return skip("Nothing to send")
        emails.send(content, emailType, currentExecutionId)
    }

    override fun composeQueued(payload: String): EmailContent? = compose(objectMapper.readValue(payload, payloadType))
}
