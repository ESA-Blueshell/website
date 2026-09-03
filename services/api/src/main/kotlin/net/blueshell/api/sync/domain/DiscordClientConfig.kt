package net.blueshell.api.sync.domain

import net.blueshell.clients.discord.DiscordClient
import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/**
 * Wires the Discord [DiscordApi] client. Sibling of `BrevoClientConfig`:
 * keeps the bot-token header and base URL out of the adapter layer so the
 * adapter can be unit-tested with a mock client. Active in production only
 * (test/dev get a mock from a sibling configuration in a later PR).
 *
 * The client comes from `net.blueshell.clients:discord-client`.
 * [DiscordClient.using] is used rather than [DiscordClient.create] so the
 * application's own `RestClient.Builder` — and with it the shared [JsonMapper]
 * — stays in the request path.
 */
@Configuration
@Profile("!test & !dev")
class DiscordClientConfig {
    @Bean
    fun discordApi(
        restClientBuilder: RestClient.Builder,
        jsonMapper: JsonMapper,
        @Value($$"${discord.botToken:}") botToken: String,
        @Value($$"${discord.baseUrl:https://discord.com/api/v10}") baseUrl: String,
    ): DiscordApi =
        DiscordClient.using(
            restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bot $botToken")
                .configureMessageConverters {
                    it.registerDefaults().withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
                }
                .build()
        )
}
