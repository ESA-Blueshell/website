package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailComposer
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.jobs.api.JobExecutionService
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EmailResendsTest {
    private val emails: EmailService = mock()
    private val executions: JobExecutionService = mock()
    private val jobs: JobQueue = mock()

    private fun composer(
        type: String,
        mayResend: Boolean,
    ) = object : EmailComposer {
        override val jobType = type
        override val emailType = "email.$type"
        override val resendable = mayResend

        override fun composeQueued(payload: String) = null
    }

    private val records: EmailRepository = mock()
    private val resends =
        EmailResends(emails, records, QueuedEmails(listOf(composer("signup", true), composer("recovery", false)), emails), executions, jobs)

    private fun email(
        id: Long,
        status: EmailDeliveryStatus,
        type: String = "email.signup",
        job: Long? = 5,
    ): Email =
        Email(emailType = type, deliveryStatus = status, jobExecutionId = job).also {
            it.id = id
            whenever(emails.findById(id)).thenReturn(it)
        }

    private fun jobIs(status: JobExecutionStatus) {
        whenever(executions.findById(5)).thenReturn(JobExecution(jobType = "signup", status = status).apply { id = 5 })
    }

    private fun queuedAgain(id: Long) =
        object : QueuedJob {
            override val id = id
            override val jobType = "signup"
            override val payload = "{}"
            override val actor = Actor.system()
        }

    private fun refusal(id: Long) =
        assertThatThrownBy { resends.resend(id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode).isEqualTo(HttpStatus.CONFLICT) }

    @Test
    fun `makes a failed or bounced email again and links the new one to it`() {
        val bounced = email(1, EmailDeliveryStatus.BOUNCED)
        val made = Entities.email(2)
        jobIs(JobExecutionStatus.SUCCESS)
        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(queuedAgain(9))
        whenever(emails.linkResend(9, bounced)).thenReturn(made)

        assertThat(resends.resend(1)).isSameAs(made)
        assertThat(resends.mayResend(bounced)).isTrue()
        assertThat(resends.mayResend(email(3, EmailDeliveryStatus.FAILED))).isTrue()
    }

    @Test
    fun `lists the emails made again from one, oldest first`() {
        val first = Entities.email(2)
        val second = Entities.email(3)
        whenever(records.findByResentFromIdOrderByIdAsc(1)).thenReturn(listOf(first, second))

        assertThat(resends.madeFrom(1)).containsExactly(first, second)
    }

    @Test
    fun `refuses an email that went, since making it again would send the person a second one`() {
        val went = listOf(EmailDeliveryStatus.QUEUED, EmailDeliveryStatus.SENT, EmailDeliveryStatus.DELIVERED, EmailDeliveryStatus.OPENED)
        for (status in went) {
            assertThat(resends.mayResend(email(1, status))).isFalse()
            refusal(1).hasMessageContaining("failed or bounced")
        }
        verify(jobs, never()).runAgain(any(), any())
    }

    @Test
    fun `refuses an email whose job keeps it to its first send, one no job sent and one of a kind it does not know`() {
        refusal(email(1, EmailDeliveryStatus.BOUNCED, type = "email.recovery").id!!).hasMessageContaining("personal link")
        refusal(email(2, EmailDeliveryStatus.FAILED, job = null).id!!).hasMessageContaining("cannot be resent")
        refusal(email(3, EmailDeliveryStatus.FAILED, type = "email.bulk").id!!).hasMessageContaining("cannot be resent")
        verify(jobs, never()).runAgain(any(), any())
    }

    @Test
    fun `refuses an email that was made again already, so a second press cannot mail the person twice`() {
        val bounced = email(1, EmailDeliveryStatus.BOUNCED)
        whenever(records.existsByResentFromId(1)).thenReturn(true)

        assertThat(resends.mayResend(bounced)).isFalse()
        refusal(1).hasMessageContaining("already resent")
        verify(jobs, never()).runAgain(any(), any())
    }

    @Test
    fun `refuses while the email's own job will still try again, so the two cannot both send`() {
        email(1, EmailDeliveryStatus.FAILED)
        for (status in listOf(JobExecutionStatus.QUEUED, JobExecutionStatus.RUNNING)) {
            jobIs(status)

            refusal(1).hasMessageContaining("still being sent")
        }
        verify(jobs, never()).runAgain(any(), any())
    }

    @Test
    fun `refuses where the same email is queued already, and where what it is about is gone`() {
        val failed = email(1, EmailDeliveryStatus.FAILED)
        jobIs(JobExecutionStatus.DEAD)

        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(null)
        refusal(1).hasMessageContaining("already queued")

        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(queuedAgain(9))
        whenever(emails.linkResend(9, failed)).thenReturn(null)
        refusal(1).hasMessageContaining("no longer exists")
    }
}
