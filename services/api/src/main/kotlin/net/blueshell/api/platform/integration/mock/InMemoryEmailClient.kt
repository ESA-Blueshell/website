package net.blueshell.api.platform.integration.mock

import net.blueshell.api.email.domain.EmailTransportClient
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory capture mock of [EmailTransportClient] used in the test profile.
 *
 * Sent messages land in [sentEmails] for assertion; [simulateSendFailure]
 * forces the next `send(...)` to throw. Drop-in replacement for the former
 * the former MockListmonkEmailClient.
 */
@Component
@Primary
@Profile("test")
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
    ): String {
        if (shouldFail) throw RuntimeException("Simulated send failure")

        _sentEmails.add(
            SentEmail(
                toEmail = toEmail,
                toName = toName,
                subject = subject,
                htmlContent = htmlContent,
                senderName = senderName,
                senderAddress = senderAddress,
                replyToAddress = replyToAddress,
            )
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
    )
}
