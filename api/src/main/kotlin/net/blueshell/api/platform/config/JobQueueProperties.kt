package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jobs")
data class JobQueueProperties(
    val queueName: String = "jobs.queue",
    val exchangeName: String = "jobs.exchange",
    val routingKey: String = "jobs.routing",
    val maxRetries: Int = 3,
    val initialBackoffMillis: Long = 1000,
    val backoffMultiplier: Double = 2.0
)
