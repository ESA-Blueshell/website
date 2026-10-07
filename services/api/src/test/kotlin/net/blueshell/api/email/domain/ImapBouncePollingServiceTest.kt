package net.blueshell.api.email.domain

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Provider
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.URLName
import jakarta.mail.internet.MimeMessage
import jakarta.mail.search.SearchTerm
import net.blueshell.api.email.persistence.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mock.env.MockEnvironment
import java.util.Properties

@ExtendWith(OutputCaptureExtension::class)
class ImapBouncePollingServiceTest {
    private val emailService: EmailService = mock()
    private val environment = MockEnvironment().withProperty("email.bounce.imap.password", "first")
    private val poller =
        ImapBouncePollingService(emailService, "mail.test", 993, "bounce@example.com", environment, "INBOX", false).apply {
            sessionFor = { protocol ->
                Session.getInstance(Properties()).apply {
                    setProvider(Provider(Provider.Type.STORE, protocol, RecordingStore::class.java.name, "test", "1"))
                }
            }
        }

    /** Records the password each connect offers, and holds one empty folder for a poll to search. */
    class RecordingStore(
        session: Session,
        url: URLName?,
    ) : Store(session, url) {
        override fun protocolConnect(
            host: String?,
            port: Int,
            user: String?,
            password: String?,
        ): Boolean {
            logins += password.orEmpty()
            return true
        }

        override fun getDefaultFolder() = throw UnsupportedOperationException()

        override fun getFolder(name: String?): Folder = inbox

        override fun getFolder(url: URLName?) = throw UnsupportedOperationException()

        companion object {
            val logins = mutableListOf<String>()
            val inbox: Folder = mock { on { search(any<SearchTerm>()) } doReturn emptyArray() }
        }
    }

    @Test
    fun `a poll connects over the protocol the TLS switch names`() {
        val real = ImapBouncePollingService(emailService, "mail.test", 993, "bounce@example.com", environment, "INBOX", true)

        assertThat(real.sessionFor("imaps").getProperty("mail.store.protocol")).isEqualTo("imaps")
    }

    @Test
    fun `each poll logs in with the password as it stands, so a rotated one is used next`() {
        RecordingStore.logins.clear()

        poller.pollBounces()
        environment.setProperty("email.bounce.imap.password", "second")
        poller.pollBounces()

        assertThat(RecordingStore.logins).containsExactly("first", "second")
        verify(RecordingStore.inbox, times(2)).open(Folder.READ_WRITE)
        verifyNoInteractions(emailService)
    }

    @Test
    fun `a bounce is logged by its outbox id, never by the address that bounced`(output: CapturedOutput) {
        val outbox = Email(recipientEmail = "ann@example.org").also { it.id = 5 }
        whenever(emailService.findByMessageId("<known@club.test>")).thenReturn(outbox)

        poller.processOne(bounce("<known@club.test>"))
        poller.processOne(bounce("<unknown@club.test>"))
        poller.processOne(MimeMessage(Session.getInstance(Properties()), "Subject: ann@example.org\r\n\r\nHello".byteInputStream()))
        // A message that cannot be marked seen is logged, and the poll goes on.
        val stuck = mock<Message> { on { setFlag(Flags.Flag.SEEN, true) } doThrow MessagingException("read-only") }
        poller.processOne(stuck)

        verify(emailService).markBounced(any(), any())
        assertThat(output.all)
            .contains("Marked email id=5 as BOUNCED", "Bounce for a message the outbox does not hold", "Failed to process bounce message")
            .doesNotContain("ann@example.org")
    }

    private fun bounce(messageId: String) =
        MimeMessage(
            Session.getInstance(Properties()),
            """
            From: MAILER-DAEMON@club.test
            To: bounce@club.test
            Subject: Undelivered Mail Returned to Sender
            Content-Type: multipart/report; report-type=delivery-status; boundary="b"

            --b
            Content-Type: message/delivery-status

            Reporting-MTA: dns; relay.club.test
            Final-Recipient: rfc822; ann@example.org
            Action: failed
            Status: 5.1.1
            Original-Message-ID: $messageId

            --b--
            """.trimIndent().replace("\n", "\r\n").byteInputStream(),
        )
}
