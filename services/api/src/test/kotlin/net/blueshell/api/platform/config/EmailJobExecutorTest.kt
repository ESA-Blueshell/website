package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

class EmailJobExecutorTest {
    @Test
    fun `email batches run on one named thread`() {
        val executor = AsyncConfig().emailJobExecutor() as ThreadPoolTaskExecutor
        try {
            assertThat(executor.corePoolSize).isEqualTo(1)
            assertThat(executor.maxPoolSize).isEqualTo(1)
            assertThat(executor.threadNamePrefix).isEqualTo("Email-")
        } finally {
            executor.shutdown()
        }
    }
}
