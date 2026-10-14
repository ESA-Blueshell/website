package net.blueshell.api.email.domain

import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Makes a failed or bounced email again from what it is about as that stands now, such as the
 * person's current address, as a new email linked to the first.
 */
@Service
class EmailResends(
    private val emails: EmailService,
    private val records: EmailRepository,
    private val queued: QueuedEmails,
    private val executions: JobExecutionService,
    private val jobs: JobQueue,
) {
    @Transactional(readOnly = true)
    fun madeFrom(id: Long): List<Email> = records.findByResentFromIdOrderByIdAsc(id)

    /** Whether the log offers to make this email again. Whether its job has finished is checked when it is asked for. */
    fun mayResend(email: Email): Boolean = refusal(email) == null

    /**
     * One transaction, so a refusal after the job was queued takes the job back with it: the new
     * job is dispatched only once this commits.
     */
    @Transactional
    fun resend(id: Long): Email {
        val email = emails.findById(id)
        (refusal(email) ?: stillSending(email))?.let { throw conflict(it) }
        val again = jobs.runAgain(requireNotNull(email.jobExecutionId), JobTrigger.SITE_ACTION)
        val made = again?.id?.let { emails.linkResend(it, email) }
        return made ?: throw conflict(if (again == null) "The same email is already queued." else GONE)
    }

    // A failed send is tried again by its own job; made again meanwhile, both would send.
    private fun stillSending(email: Email): String? =
        "This email is still being sent. It can be resent once that has failed for good."
            .takeIf { executions.findById(requireNotNull(email.jobExecutionId)).status in UNFINISHED }

    private fun refusal(email: Email): String? =
        when {
            email.deliveryStatus !in FAILED_OR_BOUNCED -> "Only an email that failed or bounced can be resent."
            email.jobExecutionId == null -> "This email cannot be resent: no job sent it."
            queued.keptToItsFirstSend(email.emailType) -> PERSONAL_LINK
            !queued.resendable(email.emailType) -> "This kind of email cannot be resent from here."
            // Once made again, what happens next is the new email's: a second resend of this one would mail the person twice.
            email.id?.let(records::existsByResentFromId) == true -> ALREADY_RESENT
            else -> null
        }

    private fun conflict(reason: String) = ResponseStatusException(HttpStatus.CONFLICT, reason)

    private companion object {
        private val FAILED_OR_BOUNCED = setOf(EmailDeliveryStatus.FAILED, EmailDeliveryStatus.BOUNCED)
        private val UNFINISHED = setOf(JobExecutionStatus.QUEUED, JobExecutionStatus.RUNNING)
        private const val PERSONAL_LINK = "This email holds a personal link or a security notice, so it is not sent again."
        private const val ALREADY_RESENT = "This email was already resent. Open the resend to see what happened to it."
        private const val GONE = "The person or event this email is about no longer exists, so it cannot be resent."
    }
}
