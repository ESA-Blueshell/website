package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import java.time.Duration

class RateLimitPauseTest {
    private val request: HttpRequest = mock()
    private val body = byteArrayOf()
    private val slept = mutableListOf<Duration>()
    private val pause = RateLimitPause(Duration.ofSeconds(10)) { slept += it }

    private fun answer(
        status: HttpStatus,
        retryAfter: String? = null,
    ): ClientHttpResponse =
        mock {
            on { statusCode } doReturn status
            on { headers } doReturn HttpHeaders().apply { retryAfter?.let { set("Retry-After", it) } }
        }

    @Test
    fun `waits the pause Discord asks for and asks once more`() {
        val limited = answer(HttpStatus.TOO_MANY_REQUESTS, "1.5")
        val fine = answer(HttpStatus.OK)
        val execution: ClientHttpRequestExecution = mock()
        whenever(execution.execute(request, body)).thenReturn(limited, fine)

        assertThat(pause.intercept(request, body, execution)).isSameAs(fine)

        assertThat(slept).containsExactly(Duration.ofMillis(1500))
        verify(limited).close()
    }

    @Test
    fun `passes on an answer that is no rate limit, names no pause or asks too long a one`() {
        val cases = listOf(answer(HttpStatus.OK), answer(HttpStatus.TOO_MANY_REQUESTS), answer(HttpStatus.TOO_MANY_REQUESTS, "60"))
        cases.forEach { case ->
            val execution: ClientHttpRequestExecution = mock { on { execute(request, body) } doReturn case }

            assertThat(pause.intercept(request, body, execution)).isSameAs(case)
            verify(execution, times(1)).execute(request, body)
        }
        assertThat(slept).isEmpty()
    }

    @Test
    fun `sleeps for real by default`() {
        val limited = answer(HttpStatus.TOO_MANY_REQUESTS, "0.001")
        val fine = answer(HttpStatus.OK)
        val execution: ClientHttpRequestExecution = mock()
        whenever(execution.execute(request, body)).thenReturn(limited, fine)

        assertThat(RateLimitPause().intercept(request, body, execution)).isSameAs(fine)
    }
}
