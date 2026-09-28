package net.blueshell.api.email.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.shared.email.EmailContent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class EmailServiceWriteTest {
    private val manager = mock<EntityManager>()
    private val repository = mock<EmailRepository> { on { saveAndFlush(any<Email>()) } doAnswer { it.getArgument(0) } }
    private val service =
        EmailService(repository).also {
            EmailService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, manager)
        }

    @Test
    fun `reads an email, and refuses one that is not there`() {
        val email = mock<Email>()
        whenever(repository.findById(1)).thenReturn(Optional.of(email))
        whenever(repository.findById(2)).thenReturn(Optional.empty())

        assertThat(service.findById(1)).isSameAs(email)
        assertThatThrownBy { service.findById(2) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a delivery is written back, and one for an email the database lost is refused`() {
        val email = mock<Email>().also { whenever(it.id).thenReturn(3) }
        val lost = mock<Email>().also { whenever(it.id).thenReturn(4) }
        whenever(repository.existsById(3)).thenReturn(true)

        assertThat(service.markDelivered(email)).isSameAs(email)
        assertThatThrownBy { service.markDelivered(lost) }.isInstanceOf(ResponseStatusException::class.java)
        verify(manager).refresh(email)
    }

    @Test
    fun `a pending email is written, and each mark on a stored one is written back`() {
        val email = mock<Email>().also { whenever(it.id).thenReturn(3) }
        whenever(repository.existsById(3)).thenReturn(true)

        val pending = service.createPending(EmailContent("a@b.nl", "A", "Hi", "Body"), "TEST", null)
        service.markSent(email, "<m@b.nl>")
        service.markFailed(email, "SMTP", "down")
        service.markOpened(email)
        service.markBounced(email, "no such mailbox")

        assertThat(pending.subject).isEqualTo("Hi")
        verify(manager).refresh(pending)
        verify(manager, times(4)).refresh(email)
    }
}
