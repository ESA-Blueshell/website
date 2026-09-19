package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

/**
 * Ends the process once Liquibase has run, so the image can be a Job.
 * Liquibase runs during context start, so reaching here means the schema moved.
 */
@Component
@Profile("migrate")
class MigrateAndExit(
    private val context: ConfigurableApplicationContext,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        log.info("[migrate] schema is up to date; stopping")
        exitProcess(SpringApplication.exit(context, ExitCodeGenerator { 0 }))
    }

    private companion object {
        val log = LoggerFactory.getLogger(MigrateAndExit::class.java)
    }
}
