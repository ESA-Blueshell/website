package net.blueshell.api.platform.config

import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate

@Configuration
@EnableConfigurationProperties(JobQueueProperties::class)
class JobRetryConfig(
    private val properties: JobQueueProperties
) {
    @Bean
    fun jobRetryTemplate(): RetryTemplate {
        val retryTemplate = RetryTemplate()

        val retryPolicy = SimpleRetryPolicy(
            properties.maxRetries + 1,
            mapOf(
                NonRetryableJobException::class.java to false,
                IllegalArgumentException::class.java to false,
                NullPointerException::class.java to false,
                ClassCastException::class.java to false,
                Exception::class.java to true
            ),
            true
        )
        retryTemplate.setRetryPolicy(retryPolicy)

        val backOffPolicy = ExponentialBackOffPolicy()
        backOffPolicy.initialInterval = properties.initialBackoffMillis
        backOffPolicy.multiplier = properties.backoffMultiplier
        backOffPolicy.maxInterval = properties.initialBackoffMillis * 16
        retryTemplate.setBackOffPolicy(backOffPolicy)

        return retryTemplate
    }
}
