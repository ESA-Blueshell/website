package net.blueshell.api.email.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stands in for [SmtpEmailClient] where no relay host is set, which the test profile arranges.
 * Sent messages land in [sentEmails] for assertion; [simulateSendFailure] forces the next
 * `send(...)` to throw.
 */
@Component
@WhenCredentialsMissing(Credentials.SMTP_HOST)
class InMemoryEmailClient : EmailTransportClient {
    // Thread-safe because email jobs run async, and a send that queues two at once has two
    // threads adding at the same moment. A plain ArrayList loses one of them, and the loss
    // reads as an email that was never sent.
    private val _sentEmails = CopyOnWriteArrayList<SentEmail>()
    val sentEmails: List<SentEmail> get() = _sentEmails.toList()

    @Volatile
    private var shouldFail = false

    fun simulateSendFailure() {
        shouldFail = true
    }

    fun stopSimulateSendFailure() {
        shouldFail = false
    }

    fun reset() {
        _sentEmails.clear()
        shouldFail = false
    }

    override fun send(
        toEmail: String,
        toName: String,
        subject: String,
        htmlContent: String,
        senderName: String,
        senderAddress: String,
        replyToAddress: String,
        threadHeaders: Map<String, String>,
    ): String {
        if (shouldFail) throw IllegalStateException("Simulated send failure")

        _sentEmails.add(
            SentEmail(
                toEmail = toEmail,
                toName = toName,
                subject = subject,
                htmlContent = htmlContent,
                senderName = senderName,
                senderAddress = senderAddress,
                replyToAddress = replyToAddress,
                threadHeaders = threadHeaders,
            ),
        )
        return "<mock-${System.nanoTime()}@blueshell.test>"
    }

    data class SentEmail(
        val toEmail: String,
        val toName: String,
        val subject: String,
        val htmlContent: String,
        val senderName: String,
        val senderAddress: String,
        val replyToAddress: String,
        val threadHeaders: Map<String, String> = emptyMap(),
    )
}
