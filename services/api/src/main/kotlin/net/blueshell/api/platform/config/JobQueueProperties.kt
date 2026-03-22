package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jobs")
data class JobQueueProperties(
    val maxRetries: Int = 3,
    val initialBackoffMillis: Long = 1000,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMillis: Long = 16_000,
    val staleThresholdMinutes: Long = 30,
    val staleRecoveryBatchSize: Int = 50,
    val autoDispatch: Boolean = true
)
