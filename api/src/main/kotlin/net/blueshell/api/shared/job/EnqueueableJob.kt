package net.blueshell.api.shared.job

/**
 * Bundles a typed [JobDefinition] with its payload so that it can be dispatched
 * without losing the type parameter.
 *
 * Usage:
 * ```kotlin
 * provider.contactSyncJob(userId).enqueueOn(jobs)
 * ```
 */
data class EnqueueableJob<T : Any>(
    val definition: JobDefinition<T>,
    val payload: T,
) {
    fun enqueueOn(dispatcher: TrackedJobDispatcher): JobExecution? =
        dispatcher.enqueue(definition, payload)
}
