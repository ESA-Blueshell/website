package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryEmailClientTest {
    @Test
    fun `keeps the thread headers a reply was sent with, and none for an email that starts a thread`() {
        val client = InMemoryEmailClient()

        client.send("a@b.nl", "Ann", "Re: Hi", "<p>Hi</p>", "Blueshell", "site@b.nl", "board@b.nl", mapOf("In-Reply-To" to "<m@x>"))

        assertThat(client.sentEmails.single().threadHeaders).containsEntry("In-Reply-To", "<m@x>")
        assertThat(InMemoryEmailClient.SentEmail("a", "b", "c", "d", "e", "f", "g").threadHeaders).isEmpty()
    }
}
