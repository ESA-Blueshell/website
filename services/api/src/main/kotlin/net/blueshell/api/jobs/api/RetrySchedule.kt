package net.blueshell.api.jobs.api

import java.time.Duration

/**
 * How often and how far apart a job retries, for a job the queue's own schedule does not suit: a
 * push to a service that may be down for hours rather than seconds.
 */
data class RetrySchedule(
    val maxRetries: Int,
    val initialBackoff: Duration,
    val multiplier: Double,
    val maxBackoff: Duration,
)
