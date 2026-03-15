package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.email.adapter.EmailTransportClient
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * In-memory mock of [net.blueshell.api.platform.integration.email.EmailTransportClient] for the test profile.
 *
 * Captures all sent emails in [sentEmails] for test assertions.
 * Supports [simulateSendFailure] / [stopSimulateSendFailure] for error-path tests.
 */
@Component
@Primary
@Profile("test")
class MockListmonkEmailClient : EmailTransportClient {

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

        val email = SentEmail(
            toEmail = toEmail,
            toName = toName,
            subject = subject,
            htmlContent = htmlContent,
            senderName = senderName,
            senderAddress = senderAddress,
            replyToAddress = replyToAddress,
        )
        _sentEmails.add(email)
        return "<mock-${System.nanoTime()}@listmonk.test>"
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