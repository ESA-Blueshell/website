package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.model.GuildFeatures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
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

        val environment = MockEnvironment().withProperty("discord.botToken", "abc")
        val rest = config.discordRestClient(builder, JsonMapper.builder().build(), environment, "https://discord.test")
        val answer =
            rest
                .get()
                .uri("/users/@me")
                .retrieve()
                .body(String::class.java)

        assertThat(answer).contains("\"1\"")
        assertThat(config.discordApi(rest)).isNotNull()
        discord.verify()
    }

    @Test
    fun `reads a guild that has features Discord added after the client was generated`() {
        val builder = RestClient.builder()
        val discord = MockRestServiceServer.bindTo(builder).build()
        discord
            .expect(requestTo("https://discord.test/guilds/1?with_counts=false"))
            .andRespond(withSuccess(guild(features = """["COMMUNITY", "TEXT_IN_VOICE_ENABLED"]"""), MediaType.APPLICATION_JSON))
        val config = DiscordClientConfig()
        val environment = MockEnvironment().withProperty("discord.botToken", "abc")
        val api = config.discordApi(config.discordRestClient(builder, JsonMapper.builder().build(), environment, "https://discord.test"))

        val read = api.getGuild("1", false)

        assertThat(read.emojis.map { it.name }).containsExactly("blueshell")
        assertThat(read.features).containsExactly(GuildFeatures.COMMUNITY)
        discord.verify()
    }

    private fun guild(features: String) =
        """
        {
          "id": "1", "name": "Blueshell", "icon": null, "description": null, "home_header": null,
          "splash": null, "discovery_splash": null, "banner": null, "owner_id": "2",
          "application_id": null, "region": "europe", "afk_channel_id": null, "afk_timeout": 300,
          "system_channel_id": null, "system_channel_flags": 0, "widget_enabled": false,
          "widget_channel_id": null, "verification_level": 1, "roles": [],
          "default_message_notifications": 1, "mfa_level": 0, "explicit_content_filter": 2,
          "max_presences": null, "max_members": 500000, "max_stage_video_channel_users": 50,
          "max_video_channel_users": 25, "vanity_url_code": null, "premium_tier": 1,
          "premium_subscription_count": 2, "preferred_locale": "en-US", "rules_channel_id": null,
          "safety_alerts_channel_id": null, "public_updates_channel_id": null,
          "premium_progress_bar_enabled": false, "nsfw": false, "nsfw_level": 0,
          "features": $features, "stickers": [],
          "emojis": [{"id": "3", "name": "blueshell", "roles": [], "require_colons": true,
            "managed": false, "animated": false, "available": true}]
        }
        """.trimIndent()
}
