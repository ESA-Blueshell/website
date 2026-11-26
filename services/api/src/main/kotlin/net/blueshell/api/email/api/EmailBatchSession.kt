package net.blueshell.api.email.api

import net.blueshell.api.jobs.api.EmailBatchPort

/**
 * Runs a batch of email sends in one SMTP session. Outside [open] a send uses its own connection;
 * inside it every send on this thread reuses the one transport.
 */
interface EmailBatchSession : EmailBatchPort {
    override fun <T> open(block: () -> T): T
}
