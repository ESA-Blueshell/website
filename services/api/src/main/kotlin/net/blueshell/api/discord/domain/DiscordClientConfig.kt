package net.blueshell.api.discord.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingHeader
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import net.blueshell.clients.discord.DiscordClient
import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/**
 * Wires the Discord [DiscordApi] client, wherever a bot token is set: in production from Vault,
 * and in dev from `.api.env` with the dev bot. Without one there is no client, and the reads that
 * need it answer that Discord is unavailable. Never in the test profile, which talks to no vendor.
 *
 * The client comes from `net.blueshell.clients:discord-client`.
 * [DiscordClient.using] is used rather than [DiscordClient.create] so the
 * application's own `RestClient.Builder` — and with it the shared [JsonMapper]
 * — stays in the request path.
 */
@Configuration
@WhenCredentialsSet(Credentials.DISCORD_BOT)
class DiscordClientConfig {
    // Also used bare for what the generated client cannot send, such as a message with a file.
    @Bean
    fun discordRestClient(
        restClientBuilder: RestClient.Builder,
        jsonMapper: JsonMapper,
        environment: Environment,
        @Value($$"${discord.baseUrl:https://discord.com/api/v10}") baseUrl: String,
    ): RestClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .requestInterceptor(RotatingHeader("Authorization", RotatingSecret(environment, Credentials.DISCORD_BOT), prefix = "Bot "))
            .requestInterceptor(RateLimitPause())
            .configureMessageConverters {
                it.registerDefaults().withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
            }.build()

    @Bean
    fun discordApi(discordRestClient: RestClient): DiscordApi = DiscordClient.using(discordRestClient)
}
