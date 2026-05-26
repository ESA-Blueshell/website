package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {
    /** General-purpose executor for fast, in-process async work (command dispatch). */
    @Bean
    fun taskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 20
        executor.queueCapacity = 1000
        executor.setThreadNamePrefix("Cmd-")
        executor.initialize()
        return executor
    }

    /** Dedicated executor for slow external-API work (Brevo, Google Calendar). */
    @Bean
    fun externalApiExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 20
        executor.queueCapacity = 200
        executor.setThreadNamePrefix("ExtApi-")
        executor.initialize()
        return executor
    }
}
