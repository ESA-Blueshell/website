package net.blueshell.api.shared.event

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

class TrackedEventPublisherTest {
    private val events = mock<AfterCommitEventPublisher>()
    private val actors = mock<ActorProvider>()
    private val within = mock<ApplicationEventPublisher>()
    private val publisher = TrackedEventPublisher(events, actors, within)

    @Test
    fun `publishes event built with current attribution`() {
        whenever(actors.currentOrSystem()).thenReturn(
            Actor(userId = 7L, type = ActionActorType.USER, role = Role.BOARD),
        )

        publisher.publish { actor ->
            TestEvent(actor)
        }

        val captor = argumentCaptor<Any>()
        verify(events).publish(captor.capture())
        assertThat(captor.firstValue)
            .isEqualTo(
                TestEvent(
                    actor = Actor(userId = 7L, type = ActionActorType.USER, role = Role.BOARD),
                ),
            )
    }

    @Test
    fun `publishes within the open transaction where asked, with the same attribution`() {
        whenever(actors.currentOrSystem()).thenReturn(Actor.system())

        publisher.publishWithin { actor -> TestEvent(actor) }

        verify(within).publishEvent(TestEvent(Actor.system()))
    }

    private data class TestEvent(
        val actor: Actor,
    )
}
