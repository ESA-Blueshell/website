package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.shared.tracking.ActorTracked
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TrackedJobDispatcherTest {

    private val queue = mock<JobQueue>()
    private val actors = mock<ActorProvider>()
    private val dispatcher = TrackedJobDispatcher(queue, actors)

    @Test
    fun `enqueue with typed job uses current actor`() {
        val job = TestJob
        val payload = TestPayload("hello")
        val actor = Actor(userId = 11L, type = ActionActorType.USER, role = Role.MEMBER)
        whenever(actors.currentOrSystem()).thenReturn(actor)
        whenever(queue.enqueue(eq(job), eq(payload), eq(actor))).thenReturn(mock())

        dispatcher.enqueue(job, payload)

        verify(queue).enqueue(eq(job), eq(payload), eq(actor))
    }

    @Test
    fun `enqueue with job type uses current actor`() {
        val actor = Actor.system()
        whenever(actors.currentOrSystem()).thenReturn(actor)
        whenever(queue.enqueue(eq("jobs.test"), eq("payload"), eq(actor), isNull())).thenReturn(mock())

        dispatcher.enqueue("jobs.test", "payload")

        verify(queue).enqueue(eq("jobs.test"), eq("payload"), eq(actor), isNull())
    }

    @Test
    fun `enqueue from actor forwards nested actor`() {
        val job = TestJob
        val payload = TestPayload("world")
        val trackedActor = Actor(userId = 99L, type = ActionActorType.USER, role = Role.BOARD)
        val actorTracked = object : ActorTracked {
            override val actor: Actor = trackedActor
        }
        whenever(queue.enqueue(eq(job), eq(payload), eq(trackedActor))).thenReturn(mock())

        dispatcher.enqueueFromActor(job, payload, actorTracked)

        verify(queue).enqueue(eq(job), eq(payload), eq(trackedActor))
    }

    @Test
    fun `explicit enqueue overload forwards provided actor`() {
        val actor = Actor(userId = 3L, type = ActionActorType.USER, role = Role.ADMIN)
        whenever(queue.enqueue(eq("jobs.manual"), eq(null), eq(actor), isNull())).thenReturn(mock())

        dispatcher.enqueue("jobs.manual", null, actor = actor)

        verify(queue).enqueue(eq("jobs.manual"), eq(null), eq(actor), isNull())
    }

    @Test
    fun `explicit enqueue overload accepts null actor`() {
        whenever(queue.enqueue(eq("jobs.manual"), eq(null), eq(null), isNull())).thenReturn(mock())

        dispatcher.enqueue("jobs.manual", null, actor = null)

        verify(queue).enqueue(eq("jobs.manual"), eq(null), eq(null), isNull())
    }

    private data class TestPayload(val value: String)

    private object TestJob : JobDefinition<TestPayload> {
        override val type: String = "jobs.test"
        override val payloadType: Class<TestPayload> = TestPayload::class.java
    }
}
