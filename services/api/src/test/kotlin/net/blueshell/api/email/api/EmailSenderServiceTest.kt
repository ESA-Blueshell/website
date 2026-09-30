package net.blueshell.api.email.api

import net.blueshell.api.email.domain.EmailService
import net.blueshell.api.email.domain.EmailTemplateService
import net.blueshell.api.email.domain.EmailTransportClient
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.email.EmailContent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmailSenderServiceTest {
    @Test
    fun `sends into the record its job queued, with the tracking pixel in it`() {
        val templates: EmailTemplateService = mock()
        val transport: EmailTransportClient = mock()
        val records: EmailService = mock()
        val content = EmailContent("a@b.nl", "Ann", "Hi", "Body")
        val queued = Email(trackingToken = "tok")
        whenever(templates.createEmail("a@b.nl", "Ann", "Hi", "Body")).thenReturn("<html><body>Hi</body></html>")
        whenever(records.forSend(content, "email.test", 7)).thenReturn(queued)
        whenever(transport.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("<m@b.nl>")

        EmailSenderService(templates, transport, records, "https://site", "https://api", "Blueshell", "no-reply@b.nl", "board@b.nl")
            .send(content, "email.test", 7)

        verify(
            transport,
        ).send(eq("a@b.nl"), eq("Ann"), eq("Hi"), argThat { contains("https://api/track/email/open/tok") }, any(), any(), any())
        verify(records).markSent(queued, "<m@b.nl>")
    }
}
