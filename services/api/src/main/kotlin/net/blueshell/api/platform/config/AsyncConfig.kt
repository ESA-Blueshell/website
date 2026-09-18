package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

private const val POOL_CORE_THREADS = 4
private const val POOL_MAX_THREADS = 20
private const val COMMAND_QUEUE_CAPACITY = 1000
// Shorter than the command queue: a backlog of calls to a third party is a
// reason to push back, not to hold more of them.
private const val EXTERNAL_QUEUE_CAPACITY = 200

@Configuration
@EnableAsync
class AsyncConfig {
    /** General-purpose executor for fast, in-process async work (command dispatch). */
    @Bean
    fun taskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = POOL_CORE_THREADS
        executor.maxPoolSize = POOL_MAX_THREADS
        executor.queueCapacity = COMMAND_QUEUE_CAPACITY
        executor.setThreadNamePrefix("Cmd-")
        executor.initialize()
        return executor
    }

    /** Dedicated executor for slow external-API work (Brevo, Google Calendar). */
    @Bean
    fun externalApiExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = POOL_CORE_THREADS
        executor.maxPoolSize = POOL_MAX_THREADS
        executor.queueCapacity = EXTERNAL_QUEUE_CAPACITY
        executor.setThreadNamePrefix("ExtApi-")
        executor.initialize()
        return executor
    }
}
