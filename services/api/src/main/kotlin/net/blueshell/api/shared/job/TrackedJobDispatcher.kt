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
    fun <T : Any> runAsync(job: JobDefinition<T>, payload: T): QueuedJob? {
        return queue.runAsync(job, payload, actors.currentOrSystem())
    }

    fun <T : Any> runAsyncFromActor(job: JobDefinition<T>, payload: T, actor: ActorTracked): QueuedJob? {
        return queue.runAsync(job, payload, actor.actor)
    }

    @Deprecated("Prefer the typed runAsync(JobDefinition, payload) overload.")
    fun runAsync(
        jobType: String,
        payload: Any? = null,
        actor: Actor? = null
    ): QueuedJob? {
        return queue.runAsync(jobType, payload, actor ?: actors.currentOrSystem())
    }
}
