package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

class DiscordClientConfigTest {
    @Test
    fun `sends the bot's token to the base URL, for the generated client and the bare one alike`() {
        val builder = RestClient.builder()
        val discord = MockRestServiceServer.bindTo(builder).build()
        discord
            .expect(requestTo("https://discord.test/users/@me"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bot abc"))
            .andRespond(withSuccess("""{"id": "1"}""", MediaType.APPLICATION_JSON))
        val config = DiscordClientConfig()

        val rest = config.discordRestClient(builder, JsonMapper.builder().build(), "abc", "https://discord.test")
        val answer = rest.get().uri("/users/@me").retrieve().body(String::class.java)

        assertThat(answer).contains("\"1\"")
        assertThat(config.discordApi(rest)).isNotNull()
        discord.verify()
    }
}
