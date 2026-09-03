package net.blueshell.api.contact.domain

import net.blueshell.clients.brevo.BrevoClient
import net.blueshell.clients.brevo.api.ContactsApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

/**
 * Wires the Brevo [ContactsApi] client. Kept separate from [BrevoContactAdapter]
 * so the adapter holds no HTTP-setup concerns and can be unit-tested with a mock
 * client. Active in production only (test/dev use MockContactAdapter).
 *
 * The client itself comes from `net.blueshell.clients:brevo-client`, generated
 * and released from ESA-Blueshell/brevo-client. [BrevoClient.using] is used
 * rather than [BrevoClient.create] so the application's own `RestClient.Builder`
 * — and with it the shared [JsonMapper] and any observability the Boot
 * auto-configuration attached — stays in the request path.
 */
@Configuration
@Profile("!test & !dev")
class BrevoClientConfig {
    @Bean
    fun brevoContactsApi(
        restClientBuilder: RestClient.Builder,
        jsonMapper: JsonMapper,
        @Value($$"${brevo.apiKey:}") apiKey: String,
        @Value($$"${brevo.baseUrl:https://api.brevo.com/v3}") baseUrl: String,
    ): ContactsApi =
        BrevoClient.using(
            restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .configureMessageConverters {
                    it.registerDefaults().withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper))
                }
                .build()
        ).contacts
}
