package net.blueshell.api.shared.job

/**
 * Bundles a typed [JobDefinition] with its payload so that it can be dispatched
 * without losing the type parameter.
 *
 * Usage:
 * ```kotlin
 * provider.contactSyncJob(userId).runAsyncOn(jobs)
 * ```
 */
data class AsyncJob<T : Any>(
    val definition: JobDefinition<T>,
    val payload: T,
) {
    fun runAsyncOn(dispatcher: TrackedJobDispatcher): QueuedJob? =
        dispatcher.runAsync(definition, payload)
}
