package net.blueshell.api.contact.domain

import net.blueshell.api.shared.credentials.RotatingSecret
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
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
    fun `the Brevo client sends the key as it stands on each call`() {
        val builder = RestClient.builder()
        val brevo = MockRestServiceServer.bindTo(builder).build()
        val api = BrevoClientConfig().brevoContactsApi(builder, JsonMapper(), environment, "http://brevo.test")
        environment.setProperty("brevo.apiKey", "rotated")

        brevo
            .expect(requestTo("http://brevo.test/contacts/attributes"))
            .andExpect(header("api-key", "rotated"))
            .andRespond(withSuccess("""{"attributes":[]}""", MediaType.APPLICATION_JSON))
        api.getAttributes()

        brevo.verify()
    }
}
