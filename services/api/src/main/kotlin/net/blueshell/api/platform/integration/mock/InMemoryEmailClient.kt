package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.email.adapter.EmailTransportClient
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

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

    private val _sentEmails = mutableListOf<SentEmail>()
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
