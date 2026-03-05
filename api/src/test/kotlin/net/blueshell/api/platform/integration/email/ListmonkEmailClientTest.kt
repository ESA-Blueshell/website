package net.blueshell.api.platform.integration.email

import net.blueshell.api.platform.integration.email.adapter.ListmonkEmailClient

import net.blueshell.clients.listmonk.api.TransactionalApi
import net.blueshell.clients.listmonk.model.TransactionalMessage
import net.blueshell.clients.listmonk.model.TransactionalMessageSubscriberMode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [ListmonkEmailClient].
 *
 * Verifies that outbound transactional emails are correctly assembled and forwarded
 * to the Listmonk API. No Spring context is required.
 */
class ListmonkEmailClientTest {

    private val transactionalApi: TransactionalApi = mock()
    private val client = ListmonkEmailClient(transactionalApi, templateId = 5)

    @BeforeEach
    fun setUp() {
        // Default: transactWithSubscriber returns null (Listmonk always returns {"data":true})
        whenever(transactionalApi.transactWithSubscriber(any())).thenReturn(null)
    }

    @Nested
    inner class SendMessageFields {

        @Test
        fun `sets subscriber email from toEmail parameter`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().subscriberEmail).isEqualTo("to@example.com")
        }

        @Test
        fun `sets subscriber mode to external`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().subscriberMode).isEqualTo(TransactionalMessageSubscriberMode.EXTERNAL)
        }

        @Test
        fun `sets configured template id`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().templateId).isEqualTo(5 as Integer)
        }

        @Test
        fun `sets subject from parameter`() {
            client.send("to@example.com", "To Name", "Test Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().subject).isEqualTo("Test Subject")
        }

        @Test
        fun `sets from email combining sender name and address`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "My Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().fromEmail).isEqualTo("My Sender <from@example.com>")
        }

        @Test
        fun `passes html content as data body`() {
            val html = "<html><body><h1>Hello</h1></body></html>"
            client.send("to@example.com", "To Name", "Subject", html, "Sender", "from@example.com", "reply@example.com")

            val data = capturedData()
            assertThat(data).containsEntry("body", html)
        }

        @Test
        fun `sets reply-to header`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            val replyToHeader = capturedHeaders().find { it.containsKey("Reply-To") }
            assertThat(replyToHeader).isNotNull
            assertThat(replyToHeader!!["Reply-To"]).isEqualTo("reply@example.com")
        }

        @Test
        fun `sets message-id header with uuid format`() {
            val messageId = client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            val msgIdHeader = capturedHeaders().find { it.containsKey("Message-ID") }
            assertThat(msgIdHeader).isNotNull
            assertThat(msgIdHeader!!["Message-ID"]).isEqualTo(messageId)
        }

        @Test
        fun `includes exactly two headers (Reply-To and Message-ID)`() {
            client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(capturedMessage().headers).hasSize(2)
        }
    }

    @Nested
    inner class ReturnValue {

        @Test
        fun `returns message id with uuid-at-listmonk format`() {
            val messageId = client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            assertThat(messageId).matches("<[0-9a-f\\-]{36}@listmonk>")
        }

        @Test
        fun `returned message id matches Message-ID header value`() {
            val messageId = client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")

            val msgIdHeader = capturedHeaders().find { it.containsKey("Message-ID") }!!
            assertThat(msgIdHeader["Message-ID"]).isEqualTo(messageId)
        }

        @Test
        fun `each send produces a unique message id`() {
            val id1 = client.send("a@example.com", "A", "Subject", "<p/>", "Sender", "from@example.com", "reply@example.com")
            val id2 = client.send("b@example.com", "B", "Subject", "<p/>", "Sender", "from@example.com", "reply@example.com")

            assertThat(id1).isNotEqualTo(id2)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `propagates exception thrown by transactional api`() {
            doThrow(RuntimeException("Listmonk API error")).whenever(transactionalApi).transactWithSubscriber(any())

            assertThatThrownBy {
                client.send("to@example.com", "To Name", "Subject", "<p>body</p>", "Sender", "from@example.com", "reply@example.com")
            }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("Listmonk API error")
        }
    }

    // ---- helpers ----

    private fun capturedMessage(): TransactionalMessage {
        val captor = argumentCaptor<TransactionalMessage>()
        verify(transactionalApi).transactWithSubscriber(captor.capture())
        return captor.firstValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun capturedData(): Map<String, Any> =
        capturedMessage().data as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    private fun capturedHeaders(): List<Map<String, Any>> =
        (capturedMessage().headers ?: emptyList()).map { it as Map<String, Any> }
}
