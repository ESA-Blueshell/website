package net.blueshell.api.job.base

/**
 * Marker interface for all jobs.
 * Job classes should implement this interface and be annotated with @Component.
 */
interface Job {
    /**
     * Execute the job
     */
    @Throws(Exception::class)
    fun execute()
}