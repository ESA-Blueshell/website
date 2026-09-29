package net.blueshell.api.contact.domain

import net.blueshell.api.shared.credentials.RotatingSecret
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.URI

class BrevoApiKeyHeaderTest {
    private val environment = MockEnvironment().withProperty("brevo.apiKey", "first")
    private val header = BrevoApiKeyHeader(RotatingSecret(environment, "brevo.apiKey"))
    private val execution = ClientHttpRequestExecution { _, _ -> MockClientHttpResponse() }

    private fun keySent(): String? {
        val request = MockClientHttpRequest(HttpMethod.GET, URI("https://api.brevo.com/v3/contacts"))
        header.intercept(request, ByteArray(0), execution)
        return request.headers.getFirst("api-key")
    }

    @Test
    fun `each request carries the key as it stands, so a rotated one is sent next`() {
        assertThat(keySent()).isEqualTo("first")

        environment.setProperty("brevo.apiKey", "second")

        assertThat(keySent()).isEqualTo("second")
    }

    @Test
    fun `the Brevo client is built on the rotating header`() {
        val api = BrevoClientConfig().brevoContactsApi(RestClient.builder(), JsonMapper(), environment, "http://brevo.test")

        assertThat(api).isNotNull
    }
}
