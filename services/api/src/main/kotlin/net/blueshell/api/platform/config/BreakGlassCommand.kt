package net.blueshell.api.platform.config

import net.blueshell.api.auth.domain.BreakGlass
import net.blueshell.api.auth.domain.BreakGlassAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Runs one break-glass action and ends the process, so the image can be a one-off Job
 * (`platform/docs/break-glass.md`). The emails it queues are durable: what has not gone out by
 * the time it stops is sent by the running api.
 */
@Component
@Profile("break-glass")
class BreakGlassCommand(
    private val context: ConfigurableApplicationContext,
    private val breakGlass: BreakGlass,
    @param:Value($$"${break-glass.action}") private val action: BreakGlassAction,
    @param:Value($$"${break-glass.username}") private val username: String,
    @param:Value($$"${break-glass.reason}") private val reason: String,
    @param:Value($$"${break-glass.dispatch-grace:15s}") private val dispatchGrace: Duration,
) {
    internal var exit: (Int) -> Unit = ::exitProcess

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val code =
            try {
                breakGlass.run(action, username, reason)
                log.info("[break-glass] {} done for {}", action, username)
                Thread.sleep(dispatchGrace.toMillis())
                0
            } catch (e: RuntimeException) {
                log.error("[break-glass] {} failed for {}", action, username, e)
                1
            }
        exit(SpringApplication.exit(context, ExitCodeGenerator { code }))
    }

    private companion object {
        val log = LoggerFactory.getLogger(BreakGlassCommand::class.java)
    }
}
