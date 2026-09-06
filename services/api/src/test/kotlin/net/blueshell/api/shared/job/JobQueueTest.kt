package net.blueshell.api.shared.job

import net.blueshell.api.shared.enums.ActionActorType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JobQueueTest {

    private val queue = mock<JobQueue>()

    @Test
    fun `runAsyncFromActor queues the job for the actor the tracked thing records`() {
        val job = TestJob
        val payload = TestPayload("world")
        val trackedActor = Actor(userId = 99L, type = ActionActorType.USER, role = Role.BOARD)
        val actorTracked = object : ActorTracked {
            override val actor: Actor = trackedActor
        }
        whenever(queue.runAsync(eq(job), eq(payload), eq(trackedActor))).thenReturn(mock())

        queue.runAsyncFromActor(job, payload, actorTracked)

        verify(queue).runAsync(eq(job), eq(payload), eq(trackedActor))
    }

    private data class TestPayload(val value: String)

    private object TestJob : JobDefinition<TestPayload> {
        override val type: String = "jobs.test"
        override val payloadType: Class<TestPayload> = TestPayload::class.java
    }
}
