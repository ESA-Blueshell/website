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

class TrackedEventPublisherTest {

    private val events = mock<AfterCommitEventPublisher>()
    private val actors = mock<ActorProvider>()
    private val publisher = TrackedEventPublisher(events, actors)

    @Test
    fun `publishes event built with current attribution`() {
        whenever(actors.currentOrSystem()).thenReturn(
            Actor(userId = 7L, type = ActionActorType.USER, role = Role.BOARD)
        )

        publisher.publish { actor ->
            TestEvent(actor)
        }

        val captor = argumentCaptor<Any>()
        verify(events).publish(captor.capture())
        assertThat(captor.firstValue)
            .isEqualTo(
                TestEvent(
                    actor = Actor(userId = 7L, type = ActionActorType.USER, role = Role.BOARD)
                )
            )
    }

    private data class TestEvent(
        val actor: Actor
    )
}
