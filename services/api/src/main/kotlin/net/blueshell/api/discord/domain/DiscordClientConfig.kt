package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.DiscordClient
import net.blueshell.clients.discord.api.DiscordApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
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
@Profile("!test")
@ConditionalOnExpression(DISCORD_TOKEN_SET)
class DiscordClientConfig {
    /* Also used bare for what the generated client cannot send, such as a message with a file. */
    @Bean
    fun discordRestClient(
        restClientBuilder: RestClient.Builder,
        jsonMapper: JsonMapper,
        @Value($$"${discord.botToken:}") botToken: String,
        @Value($$"${discord.baseUrl:https://discord.com/api/v10}") baseUrl: String,
    ): RestClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bot $botToken")
            .requestInterceptor(RateLimitPause())
            .configureMessageConverters {
                it.registerDefaults().withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
            }.build()

    @Bean
    fun discordApi(discordRestClient: RestClient): DiscordApi = DiscordClient.using(discordRestClient)
}

/** Whether a bot token is configured: the one switch every Discord bean hangs off. */
internal const val DISCORD_TOKEN_SET = "'\${discord.botToken:}' != ''"
