package net.blueshell.api.platform.integration.email

import net.blueshell.api.factory.email.persistence.EmailFactory
import net.blueshell.api.platform.config.ImapBounceProperties
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.platform.integration.mock.MockImapBounceClient
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class BounceMailboxPollerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var mockImapClient: MockImapBounceClient

    @Autowired
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var emailFactory: EmailFactory

    @Autowired
    private lateinit var parser: BounceMessageParser

    private lateinit var poller: BounceMailboxPoller

    @BeforeEach
    fun setUp() {
        mockImapClient.clear()
        val enabledProperties = ImapBounceProperties(enabled = true)
        poller = BounceMailboxPoller(mockImapClient, parser, emailService, enabledProperties, io.micrometer.core.instrument.simple.SimpleMeterRegistry())
    }

    @Test
    fun `matched bounce updates email to BOUNCED status`() {
        val messageId = "<test-bounce-${System.nanoTime()}@blueshell.utwente.nl>"
        val email = emailFactory.create(
            deliveryStatus = EmailDeliveryStatus.SENT,
            messageId = messageId,
        )

        val bounceEml = buildDsnBounce(uid = 1, originalMessageId = messageId)
        mockImapClient.enqueue(bounceEml)

        poller.poll()

        val updated = emailService.findById(email.id!!)
        assertThat(updated).isNotNull
        assertThat(updated!!.deliveryStatus).isEqualTo(EmailDeliveryStatus.BOUNCED)
        assertThat(updated.errorReason).contains("action=FAILED")
        assertThat(mockImapClient.seenUids).contains(1L)
    }

    @Test
    fun `unmatched message-id marks message seen but does not affect emails`() {
        val bounceEml = buildDsnBounce(uid = 2, originalMessageId = "<unknown-msg@example.com>")
        mockImapClient.enqueue(bounceEml)

        poller.poll()

        assertThat(mockImapClient.seenUids).contains(2L)
    }

    @Test
    fun `already-bounced email is not updated again`() {
        val messageId = "<test-already-bounced-${System.nanoTime()}@blueshell.utwente.nl>"
        val email = emailFactory.create(
            deliveryStatus = EmailDeliveryStatus.BOUNCED,
            messageId = messageId,
        )

        val bounceEml = buildDsnBounce(uid = 3, originalMessageId = messageId)
        mockImapClient.enqueue(bounceEml)

        poller.poll()

        val result = emailService.findById(email.id!!)
        assertThat(result!!.deliveryStatus).isEqualTo(EmailDeliveryStatus.BOUNCED)
        assertThat(mockImapClient.seenUids).contains(3L)
    }

    @Test
    fun `non-bounce message is skipped and marked seen`() {
        val plainMessage = RawBounceMessage(
            uid = 4,
            subject = "Re: Meeting tomorrow",
            contentType = "text/plain",
            rawBytes = """
                From: someone@example.com
                To: sitecie@blueshell.utwente.nl
                Subject: Re: Meeting tomorrow
                MIME-Version: 1.0
                Content-Type: text/plain; charset=us-ascii

                Sounds good, see you then!
            """.trimIndent().toByteArray(),
        )
        mockImapClient.enqueue(plainMessage)

        poller.poll()

        assertThat(mockImapClient.seenUids).contains(4L)
    }

    @Test
    fun `does nothing when disabled`() {
        val disabledProperties = ImapBounceProperties(enabled = false)
        val disabledPoller = BounceMailboxPoller(
            mockImapClient, parser, emailService, disabledProperties,
            io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        )
        mockImapClient.enqueue(
            buildDsnBounce(uid = 5, originalMessageId = "<any@test.com>")
        )

        disabledPoller.poll()

        assertThat(mockImapClient.seenUids).isEmpty()
    }

    private fun buildDsnBounce(uid: Long, originalMessageId: String): RawBounceMessage {
        val raw = """
            From: MAILER-DAEMON@mail.example.com
            To: sitecie@blueshell.utwente.nl
            Subject: Delivery Status Notification (Failure)
            Date: Mon, 03 Mar 2026 10:00:00 +0100
            MIME-Version: 1.0
            Content-Type: multipart/report; report-type=delivery-status; boundary="testboundary"
            Auto-Submitted: auto-generated

            --testboundary
            Content-Type: text/plain; charset=us-ascii

            Your message could not be delivered.

            --testboundary
            Content-Type: message/delivery-status

            Reporting-MTA: dns; mail.example.com

            Final-Recipient: rfc822; bounce@example.com
            Action: failed
            Status: 5.1.1
            Diagnostic-Code: smtp; 550 User unknown

            --testboundary
            Content-Type: message/rfc822

            From: sitecie@blueshell.utwente.nl
            To: bounce@example.com
            Subject: Test
            Message-ID: $originalMessageId

            Test body.

            --testboundary--
        """.trimIndent()

        return RawBounceMessage(
            uid = uid,
            subject = "Delivery Status Notification (Failure)",
            contentType = "multipart/report; report-type=delivery-status; boundary=\"testboundary\"",
            rawBytes = raw.toByteArray(),
        )
    }
}
