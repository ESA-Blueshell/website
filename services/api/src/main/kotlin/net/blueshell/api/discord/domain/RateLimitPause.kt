package net.blueshell.api.discord.domain

import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.time.Duration

/**
 * Waits out a rate limit Discord answers with and asks once more, so a burst such as the morning
 * run's is paced rather than failed. A pause longer than [longest] is left to the job's retry.
 */
internal class RateLimitPause(
    private val longest: Duration = LONGEST,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it) },
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val response = execution.execute(request, body)
        if (response.statusCode != HttpStatus.TOO_MANY_REQUESTS) return response
        val pause =
            response.headers
                .getFirst("Retry-After")
                ?.toDoubleOrNull()
                ?.let { Duration.ofMillis((it * MILLIS).toLong()) }
                ?.takeIf { it <= longest }
                ?: return response
        response.close()
        sleep(pause)
        return execution.execute(request, body)
    }

    private companion object {
        const val MILLIS = 1000
        val LONGEST: Duration = Duration.ofSeconds(10)
    }
}
