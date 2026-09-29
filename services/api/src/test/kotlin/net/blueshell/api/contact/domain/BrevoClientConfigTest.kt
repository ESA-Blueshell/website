package net.blueshell.api.contact.domain

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

class BrevoClientConfigTest {
    @Test
    fun `the Brevo client sends the key as it stands on each call`() {
        val environment = MockEnvironment().withProperty("brevo.apiKey", "first")
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
