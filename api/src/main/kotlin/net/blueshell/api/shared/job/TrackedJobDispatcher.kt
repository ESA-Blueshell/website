package net.blueshell.api.shared.job

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.shared.tracking.ActorTracked
import org.springframework.stereotype.Component

@Component
class TrackedJobDispatcher(
    private val queue: JobQueue,
    private val actors: ActorProvider
) {
    fun <T : Any> enqueue(job: JobDefinition<T>, payload: T): JobExecution? {
        return queue.enqueue(job, payload, actors.currentOrSystem())
    }

    fun enqueue(jobType: String, payload: Any? = null): JobExecution? {
        return queue.enqueue(jobType, payload, actors.currentOrSystem())
    }

    fun <T : Any> enqueueFromActor(job: JobDefinition<T>, payload: T, actor: ActorTracked): JobExecution? {
        return queue.enqueue(job, payload, actor.actor)
    }

    fun enqueue(
        jobType: String,
        payload: Any? = null,
        actor: Actor? = null
    ): JobExecution? {
        return queue.enqueue(jobType, payload, actor)
    }
}
