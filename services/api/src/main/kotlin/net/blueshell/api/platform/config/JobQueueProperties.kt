package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jobs")
data class JobQueueProperties(
    val maxRetries: Int = 9,
    val initialBackoffMillis: Long = 60_000,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMillis: Long = 14_400_000,
    val staleThresholdMinutes: Long = 30,
    val staleRecoveryBatchSize: Int = 50,
    val retryCheckIntervalMs: Long = 30_000,
    val autoDispatch: Boolean = true
)
