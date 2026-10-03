package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryEmailClientTest {
    @Test
    fun `keeps the thread headers a reply was sent with`() {
        val client = InMemoryEmailClient()

        client.send("a@b.nl", "A", "Re: S", "<p>B</p>", "Blueshell", "no-reply@b.nl", "board@b.nl", mapOf("In-Reply-To" to "<r>"))

        assertThat(client.sentEmails.single().threadHeaders).containsEntry("In-Reply-To", "<r>")
    }
}
