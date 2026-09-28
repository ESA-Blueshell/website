package net.blueshell.api.event.api

import jakarta.persistence.EntityManager
import net.blueshell.api.event.domain.EventNotFoundException
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.Optional

class EventServiceTest {
    private fun event(
        approved: Boolean,
        awaiting: Boolean = false,
    ) = Event(
        committee = mock(),
        title = "LAN party",
        startTime = Instant.parse("2026-10-10T18:00:00Z"),
        endTime = Instant.parse("2026-10-10T21:00:00Z"),
        approved = approved,
    ).apply {
        id = 42
        awaitingReapproval = awaiting
    }

    /** What an update of an event stored as [stored] to [edited] publishes. */
    private fun changeOf(
        stored: Boolean,
        edited: Boolean,
        storedAwaiting: Boolean = false,
        editedAwaiting: Boolean = false,
    ): EventChange {
        val repository: EventRepository =
            mock {
                on { findById(42) } doReturn Optional.of(event(stored, storedAwaiting))
                on { existsById(42) } doReturn true
                on { saveAndFlush(any<Event>()) } doReturn event(edited, editedAwaiting)
            }
        val published: TrackedEventPublisher = mock()
        val service = EventService(repository, mock(), mock(), mock(), published, mock())
        EventService::class.java
            .getDeclaredField("em")
            .apply { isAccessible = true }
            .set(service, mock<EntityManager>())

        service.update(event(edited))

        val factory = argumentCaptor<(Actor) -> Any>()
        verify(published).publishWithin(factory.capture())
        return (factory.firstValue(Actor.system()) as EventChanged).changeType
    }

    @Test
    fun `says when an edit turns the event's approval`() {
        assertThat(changeOf(stored = false, edited = true)).isEqualTo(EventChange.APPROVED)
        assertThat(changeOf(stored = true, edited = false)).isEqualTo(EventChange.UNAPPROVED)
        assertThat(changeOf(stored = true, edited = true)).isEqualTo(EventChange.UPDATED)
        assertThat(changeOf(stored = false, edited = false)).isEqualTo(EventChange.UPDATED)
    }

    @Test
    fun `says when an edit sends the event back to the board, and when the board declines it then`() {
        assertThat(changeOf(stored = true, edited = false, editedAwaiting = true)).isEqualTo(EventChange.SENT_BACK)
        assertThat(changeOf(stored = false, edited = false, storedAwaiting = true, editedAwaiting = true)).isEqualTo(EventChange.UPDATED)
        assertThat(changeOf(stored = false, edited = false, storedAwaiting = true)).isEqualTo(EventChange.UNAPPROVED)
        assertThat(changeOf(stored = false, edited = true, storedAwaiting = true)).isEqualTo(EventChange.APPROVED)
    }

    @Test
    fun `refuses an event that is not there, and says a deleted one is gone`() {
        val stored = event(true)
        val repository: EventRepository =
            mock {
                on { findById(42) } doReturn Optional.of(stored)
                on { findById(43) } doReturn Optional.empty()
            }
        val published: TrackedEventPublisher = mock()
        val service = EventService(repository, mock(), mock(), mock(), published, mock())

        assertThatThrownBy { service.findById(43) }.isInstanceOf(EventNotFoundException::class.java)
        service.deleteById(42)

        verify(repository).delete(stored)
        verify(published).publishWithin(any())
    }

    @Test
    fun `writes a new event and reads it back`() {
        val repository: EventRepository = mock { on { saveAndFlush(any<Event>()) } doAnswer { it.getArgument<Event>(0) } }
        val published: TrackedEventPublisher = mock()
        val manager = mock<EntityManager>()
        val service = EventService(repository, mock(), mock(), mock(), published, mock())
        EventService::class.java
            .getDeclaredField("em")
            .apply { isAccessible = true }
            .set(service, manager)
        val event = event(true)

        service.create(event)

        verify(manager).refresh(event)
    }

    @Test
    fun `lists the events that have not ended`() {
        val from = java.time.Instant.parse("2026-10-01T12:00:00Z")
        val repository: EventRepository = mock { on { findIdsEndingFrom(from) } doReturn listOf(3L) }
        val service = EventService(repository, mock(), mock(), mock(), mock(), mock())

        assertThat(service.idsEndingFrom(from)).containsExactly(3L)
    }
}
