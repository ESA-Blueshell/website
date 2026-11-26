package net.blueshell.api.jobs.api

/**
 * A batch scope the job engine runs a group of email jobs in. Implemented by the email module's
 * session, so the engine does not need to know how the transport is held open.
 */
interface EmailBatchPort {
    fun <T> open(block: () -> T): T
}
