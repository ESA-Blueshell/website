package net.blueshell.api.email.web

import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.shared.enums.EmailDeliveryStatus
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
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class EmailResendTest {
    private val emails: EmailService = mock()
    private val jobs: JobQueue = mock()
    private val controller = EmailManagementController(emails, mock(), mock(), mock(), jobs, mock(), mock())

    private fun queued(id: Long?) =
        object : QueuedJob {
            override val id = id
            override val jobType = "auth.recovery"
            override val payload = "{}"
            override val actor = Actor.system()
        }

    @Test
    fun `refuses an email with no job, one already queued again, and one whose subject is gone`() {
        whenever(emails.findById(1)).thenReturn(Entities.email(1))
        assertThatThrownBy { controller.resend(1) }.isInstanceOf(ResponseStatusException::class.java).hasMessageContaining("no linked job")

        val linked = Entities.email(2).apply { jobExecutionId = 5 }
        whenever(emails.findById(2)).thenReturn(linked)
        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(null)
        assertThatThrownBy { controller.resend(2) }.isInstanceOf(ResponseStatusException::class.java).hasMessageContaining("already queued")

        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(queued(9))
        whenever(emails.linkResend(any(), any())).thenReturn(null)
        assertThatThrownBy {
            controller.resend(
                2,
            )
        }.isInstanceOf(ResponseStatusException::class.java).hasMessageContaining("no longer exists")
    }

    @Test
    fun `answers the email made again, linked to the one it came from, and counts what is queued`() {
        val linked =
            Entities.email(2).apply {
                jobExecutionId = 5
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        val made =
            Entities.email(3).apply {
                resentFromId = 2
                senderAddress = "events@b.nl"
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        whenever(emails.findById(2)).thenReturn(linked)
        whenever(jobs.runAgain(5, JobTrigger.SITE_ACTION)).thenReturn(queued(9))
        whenever(emails.linkResend(9, linked)).thenReturn(made)
        whenever(emails.countByStatus(EmailDeliveryStatus.QUEUED)).thenReturn(4)

        assertThat(controller.resend(2).let { listOf(it.resentFromId, it.senderAddress) }).containsExactly(2L, "events@b.nl")
        whenever(emails.resendsOf(2)).thenReturn(listOf(made))
        val detail = controller.findEmail(2)
        assertThat(detail.resends.map { it.id }).containsExactly(3)
        assertThat(detail.email.initiatedByUserId).isNull()
        assertThat(controller.getStats().queuedCount).isEqualTo(4)
    }
}
