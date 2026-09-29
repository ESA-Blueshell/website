package net.blueshell.api.shared.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI

class RotatingHeaderTest {
    private val environment = MockEnvironment().withProperty("discord.botToken", "first")
    private val header = RotatingHeader("Authorization", RotatingSecret(environment, "discord.botToken"), prefix = "Bot ")
    private val execution = ClientHttpRequestExecution { _, _ -> MockClientHttpResponse() }

    private fun sent(): String? {
        val request = MockClientHttpRequest(HttpMethod.GET, URI("https://discord.test/users/@me"))
        header.intercept(request, ByteArray(0), execution)
        return request.headers.getFirst("Authorization")
    }

    @Test
    fun `each request carries the credential as it stands, so a rotated one is sent next`() {
        assertThat(sent()).isEqualTo("Bot first")

        environment.setProperty("discord.botToken", "second")

        assertThat(sent()).isEqualTo("Bot second")
    }
}
