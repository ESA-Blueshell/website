package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailComposer
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.JobQueued
import net.blueshell.api.shared.tracking.Actor
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class QueuedEmailsTest {
    private val content = EmailContent("a@b.nl", "Ann", "Hi", "Body")

    private fun composer(
        type: String,
        compose: (String) -> EmailContent?,
    ) = object : EmailComposer {
        override val jobType = type
        override val emailType = "email.$type"

        override fun composeQueued(payload: String) = compose(payload)
    }

    @Test
    fun `records the email a queued job will send, and nothing for a job that sends none or cannot compose yet`() {
        val emails: EmailService = mock()
        val queued =
            QueuedEmails(
                listOf(composer("sends") { content }, composer("quiet") { null }, composer("gone") { error("no such row") }),
                emails,
            )

        queued.on(JobQueued(1, "sends", "{}", Actor.system()))
        verify(emails).recordQueued(content, "email.sends", 1, Actor.system())

        val nothing: EmailService = mock()
        val none = QueuedEmails(listOf(composer("quiet") { null }, composer("gone") { error("no such row") }), nothing)
        none.on(JobQueued(2, "quiet", "{}", Actor.system()))
        none.on(JobQueued(3, "gone", "{}", Actor.system()))
        none.on(JobQueued(4, "unrelated", "{}", Actor.system()))
        none.on(JobQueued(5, "quiet", null, Actor.system()))
        verifyNoInteractions(nothing)
    }
}
