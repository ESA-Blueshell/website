package net.blueshell.api.email.api

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.JobDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tools.jackson.databind.json.JsonMapper

class EmailJobTest {
    data class Payload(
        val to: String?,
    )

    private val definition =
        object : JobDefinition<Payload> {
            override val type = "test.email"
            override val payloadType = Payload::class.java
        }

    private val emails: EmailSenderService = mock()

    private val job =
        object : EmailJob<Payload>(JsonMapper(), definition, emails) {
            override fun compose(payload: Payload) = payload.to?.let { EmailContent(it, "Ann", "Hi", "Body") }
        }

    @Test
    fun `sends what the payload composes under its job's type, and skips a payload that composes nothing`() {
        assertThat(job.emailType).isEqualTo("test.email")
        assertThat(job.composeQueued("""{"to":"a@b.nl"}""")?.recipientEmail).isEqualTo("a@b.nl")

        job.handle("""{"to":"a@b.nl"}""", 3, false)
        verify(emails).send(EmailContent("a@b.nl", "Ann", "Hi", "Body"), "test.email", 3)

        val skipped = mock<EmailSenderService>()
        val quiet =
            object : EmailJob<Payload>(JsonMapper(), definition, skipped) {
                override fun compose(payload: Payload): EmailContent? = null
            }
        assertThat(quiet.handle("""{"to":null}""", 4, false)).isInstanceOf(JobOutcome.Skipped::class.java)
        verify(skipped, never()).send(any(), any(), anyOrNull())
    }
}
