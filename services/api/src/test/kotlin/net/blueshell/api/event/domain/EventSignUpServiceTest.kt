package net.blueshell.api.event.domain

import jakarta.persistence.EntityManager
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class EventSignUpServiceTest {
    private val repository = mock<EventSignUpRepository>()
    private val published = mock<ApplicationEventPublisher>()
    private val actors = mock<ActorProvider> { on { currentOrSystem() } doReturn Actor.system() }
    private val service =
        EventSignUpService(repository, TrackedEventPublisher(mock(), actors, published), mock<CurrentUserProvider>()).apply {
            // The entity manager is injected by field; create refreshes the saved row through it.
            EventSignUpService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(this, mock<EntityManager>())
        }

    private val event = mock<Event> { on { id } doReturn 100 }

    private fun countsMoved() =
        argumentCaptor<Any>().let { sent ->
            verify(published, atLeastOnce()).publishEvent(sent.capture())
            sent.allValues.filterIsInstance<EventSignUpsChanged>().map { it.eventId }
        }

    @Test
    fun `says the event's sign-up count moved on a sign-up and on each way one is taken off`() {
        val signUp = EventSignUp(event).apply { id = 5 }
        whenever(repository.saveAndFlush(signUp)).thenReturn(signUp)
        whenever(repository.existsById(5)).thenReturn(true)
        whenever(repository.findById(5)).thenReturn(Optional.of(signUp))

        service.create(signUp)
        service.delete(signUp)
        service.deleteById(5)

        assertThat(countsMoved()).containsExactly(100L, 100L, 100L)
    }

    @Test
    fun `answers whether an account already signed up for an event`() {
        whenever(repository.existsByUser_IdAndEvent_Id(9L, 100L)).thenReturn(true)
        whenever(repository.existsByUser_IdAndEvent_Id(9L, 101L)).thenReturn(false)

        assertThat(service.existsByUserIdAndEventId(9L, 100L)).isTrue()
        assertThat(service.existsByUserIdAndEventId(9L, 101L)).isFalse()
        verify(repository).existsByUser_IdAndEvent_Id(9L, 100L)
    }

    @Test
    fun `reads a sign-up back after an edit, and refuses one that is not there`() {
        val signUp = EventSignUp(event).apply { id = 5 }
        whenever(repository.saveAndFlush(signUp)).thenReturn(signUp)
        whenever(repository.findById(6)).thenReturn(Optional.empty())

        assertThat(service.update(signUp)).isSameAs(signUp)
        assertThatThrownBy { service.findById(6) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `takes a form's sign-ups away with no count published`() {
        val signUps = setOf(EventSignUp(event).apply { id = 5 })

        service.deleteAll(signUps)

        verify(repository).deleteAll(signUps)
    }
}
