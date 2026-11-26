package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailBatchSession
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.seconds

/**
 * Holds one connection per thread for the length of [open]. A connection is retired after a
 * message limit, a long idle gap, or a failed send. Mail types stay with the caller: this only
 * holds a handle and the callbacks that know how to use it.
 */
@Component
@WhenCredentialsSet(Credentials.SMTP_HOST)
class SmtpSession(
    @param:Value("\${app.email.batch.max-messages-per-connection:50}") private val maxMessages: Int = 50,
) : EmailBatchSession {
    private val state = ThreadLocal<State>()

    override fun <T> open(block: () -> T): T {
        check(state.get() == null) { "A session is already open on this thread" }
        val opened = State()
        state.set(opened)
        try {
            return block()
        } finally {
            opened.close()
            state.remove()
        }
    }

    /** Null outside a session; inside one the current connection, opened on first use. */
    fun <T> connection(
        connect: () -> T,
        connected: (T) -> Boolean,
        close: (T) -> Unit,
    ): T? {
        val current = state.get() ?: return null
        val now = System.nanoTime()
        val held = current.connection
        if (held != null && current.retired(now)) current.close()
        val existing = current.connection
        if (existing != null) {
            current.lastUsedNanos = now
            @Suppress("UNCHECKED_CAST")
            return existing as T
        }
        val opened = connect()
        current.connection = opened
        current.closeConnection = { close(opened) }
        current.isConnected = { connected(opened) }
        current.lastUsedNanos = now
        return opened
    }

    /** Counts a delivered message and retires the connection once its limit is reached. */
    fun sent() {
        val current = state.get() ?: return
        current.sent += 1
        if (current.sent >= messageLimit) current.close()
    }

    /** Drops a connection whose state may be broken after a failed send. */
    fun discard() {
        state.get()?.close()
    }

    private val messageLimit: Int get() = maxMessages.coerceAtLeast(1)

    private fun State.retired(now: Long): Boolean = sent >= messageLimit || now - lastUsedNanos > IDLE_NANOS || !isConnected()

    private class State {
        var connection: Any? = null
        var closeConnection: () -> Unit = {}
        var isConnected: () -> Boolean = { true }
        var sent: Int = 0
        var lastUsedNanos: Long = System.nanoTime()

        fun close() {
            runCatching(closeConnection).onFailure { SmtpSession.log.warn("Could not close SMTP connection", it) }
            connection = null
            closeConnection = {}
            isConnected = { true }
            sent = 0
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmtpSession::class.java)
        private val IDLE_NANOS = 60.seconds.inWholeNanoseconds
    }
}
