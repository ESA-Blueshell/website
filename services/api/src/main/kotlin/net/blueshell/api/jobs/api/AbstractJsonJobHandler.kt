package net.blueshell.api.jobs.api

import net.blueshell.api.jobs.domain.JobHandler
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobEffect
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

abstract class AbstractJsonJobHandler<T : Any>(
    private val objectMapper: ObjectMapper,
    private val definition: JobDefinition<T>,
) : JobHandler {
    // Open getters, not fields: these handlers are @Transactional, so Spring wraps them in CGLIB
    // proxies, and a proxy's own fields are empty. An open getter delegates to the target.
    override val jobType: String get() = definition.type

    override val payloadType: Class<T> get() = definition.payloadType

    private class Run(
        val executionId: Long?,
        val forced: Boolean,
    ) {
        var skipped: String? = null
        var effect: JobEffect? = null
        var link: String? = null
    }

    // Thread-local is safe because @Async jobs each run on their own thread.
    private val run = ThreadLocal<Run?>()

    /** The execution this run belongs to, for records a downstream service links back to it. */
    protected val currentExecutionId: Long?
        get() = run.get()?.executionId

    /** Whether somebody asked for this run by hand; see [JobHandler.handle]. */
    protected val forced: Boolean
        get() = run.get()?.forced ?: false

    /** Ends the run as skipped rather than done, once [handlePayload] returns. */
    protected fun skip(reason: String) {
        run.get()?.skipped = reason
    }

    /** Records what the run did to the thing it keeps, and where that thing is. */
    protected fun did(
        effect: JobEffect,
        link: String?,
    ) {
        run.get()?.apply {
            this.effect = effect
            this.link = link
        }
    }

    @Transactional
    override fun handle(
        payload: String?,
        executionId: Long?,
        forced: Boolean,
    ): JobOutcome {
        val body = payload ?: throw IllegalArgumentException("Payload required for job type $jobType")
        val current = Run(executionId, forced)
        run.set(current)
        try {
            handlePayload(objectMapper.readValue(body, payloadType))
            return current.skipped?.let(JobOutcome::Skipped) ?: JobOutcome.Done(current.effect, current.link)
        } finally {
            run.remove()
        }
    }

    protected abstract fun handlePayload(payload: T)
}
