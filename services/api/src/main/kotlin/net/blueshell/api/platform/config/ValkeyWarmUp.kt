package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Opens the Valkey connection before the pod reports ready (#1960).
 *
 * The connection is otherwise opened by the first request that needs it, and on a JVM that has
 * only just started its setup can outlast the 500 ms command timeout, which also bounds that
 * setup; that request then fails. Spring reports readiness only after this listener returns, so
 * a failed attempt is simply tried again here. Nothing stops the api coming up: after the last
 * attempt the first request opens the connection, as before.
 */
@Component
@Profile("!migrate")
class ValkeyWarmUp(
    private val connections: RedisConnectionFactory,
    private val betweenAttempts: Duration = Duration.ofSeconds(1),
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        repeat(ATTEMPTS) { attempt ->
            val answer = runCatching { connections.connection.use { it.ping() } }
            if (answer.isSuccess) {
                log.info("[valkey] connected before taking traffic, on attempt {}", attempt + 1)
                return
            }
            log.info("[valkey] attempt {} to connect failed: {}", attempt + 1, answer.exceptionOrNull()?.message)
            if (attempt < ATTEMPTS - 1) Thread.sleep(betweenAttempts)
        }
        log.warn("[valkey] not connected after {} attempts; the first request opens the connection", ATTEMPTS)
    }

    private companion object {
        const val ATTEMPTS = 5
        val log = LoggerFactory.getLogger(ValkeyWarmUp::class.java)
    }
}
