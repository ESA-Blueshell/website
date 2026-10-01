package net.blueshell.api.email.api

import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class SentEmailsTest {
    private val repository: EmailRepository = mock()
    private val sent = SentEmails(repository)
    private val reminder =
        Email(
            recipientEmail = "a@b.nl",
            subject = "Pay",
            emailType = "email.contribution-reminder",
            messageId = "<sent-9>",
            sentAt = Instant.EPOCH,
        ).also { it.id = 9 }

    @Test
    fun `finds the email a reply answers in thread order, and emails by id`() {
        whenever(repository.findByMessageIdIn(listOf("<other>", "<sent-9>"))).thenReturn(listOf(reminder))
        whenever(repository.findByIdIn(listOf(9L))).thenReturn(listOf(reminder))

        assertThat(
            sent.answeredBy(listOf("<other>", "<sent-9>")),
        ).isEqualTo(SentEmailRef(9, "email.contribution-reminder", "a@b.nl", "Pay", Instant.EPOCH))
        assertThat(sent.answeredBy(emptyList())).isNull()
        assertThat(sent.byIds(listOf(9L)).keys).containsExactly(9)
        assertThat(sent.byIds(emptyList())).isEmpty()
        whenever(repository.findTop20ByRecipientEmailOrderByIdDesc("a@b.nl")).thenReturn(listOf(reminder))
        assertThat(sent.toAddress("a@b.nl").map { it.id }).containsExactly(9)
    }
}
