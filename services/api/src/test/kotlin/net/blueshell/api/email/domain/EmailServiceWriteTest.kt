package net.blueshell.api.email.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.Entities
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
        val email = Entities.email()
        whenever(repository.findById(1)).thenReturn(Optional.of(email))
        whenever(repository.findById(2)).thenReturn(Optional.empty())

        assertThat(service.findById(1)).isSameAs(email)
        assertThatThrownBy { service.findById(2) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a delivery is written back, and one for an email the database lost is refused`() {
        val email = Entities.email(id = 3)
        val lost = Entities.email(id = 4)
        whenever(repository.existsById(3)).thenReturn(true)

        assertThat(service.markDelivered(email)).isSameAs(email)
        assertThatThrownBy { service.markDelivered(lost) }.isInstanceOf(ResponseStatusException::class.java)
        verify(manager).refresh(email)
    }

    @Test
    fun `an email with no queued record is written, and each mark on a stored one is written back`() {
        val email = Entities.email(id = 3)
        whenever(repository.existsById(3)).thenReturn(true)

        val pending = service.forSend(EmailContent("a@b.nl", "A", "Hi", "Body"), "TEST", null)
        service.markSent(email, "<m@b.nl>")
        service.markFailed(email, "SMTP", "down")
        service.markOpened(email)
        service.markBounced(email, "no such mailbox")

        assertThat(pending.subject).isEqualTo("Hi")
        verify(manager).refresh(pending)
        verify(manager, times(4)).refresh(email)
    }

    @Test
    fun `a queued job's email is recorded once, filled by its send, and linked to what it was made again from`() {
        val queued = service.recordQueued(EmailContent("old@b.nl", "A", "Hi", "Body"), "TEST", 7, Actor.user(9, Role.BOARD))
        assertThat(queued.deliveryStatus).isEqualTo(EmailDeliveryStatus.QUEUED)
        assertThat(queued.initiatedByUserId).isEqualTo(9)
        assertThat(queued.initiatedByType).isEqualTo(ActionActorType.USER)
        assertThat(service.recordQueued(EmailContent("x@b.nl", "X", "X", "X"), "TEST", 8).initiatedByType).isEqualTo(ActionActorType.SYSTEM)

        val stored = Entities.email(id = 5)
        whenever(repository.findTopByJobExecutionIdOrderByIdDesc(7)).thenReturn(stored)
        whenever(repository.existsById(5)).thenReturn(true)
        assertThat(service.recordQueued(EmailContent("old@b.nl", "A", "Hi", "Body"), "TEST", 7)).isSameAs(stored)

        val sent = service.forSend(EmailContent("new@b.nl", "Ann", "Hello", "Now"), "TEST", 7)
        assertThat(sent).isSameAs(stored)
        assertThat(sent.recipientEmail).isEqualTo("new@b.nl")
        assertThat(sent.subject).isEqualTo("Hello")
        assertThat(sent.bodyMarkdown).isEqualTo("Now")

        val first = Entities.email(id = 2)
        assertThat(service.linkResend(7, first)?.resentFromId).isEqualTo(2)
        assertThat(service.linkResend(70, first)).isNull()
    }
}
