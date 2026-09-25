package net.blueshell.api.auth.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Where somebody locked out, or worried, is sent for help. */
data class SecurityContacts(
    val email: String,
    val boardChannel: String,
    val suggestionsChannel: String,
) {
    val markdown: String
        get() =
            "email the board at [$email](mailto:$email), or ask in " +
                "[#board-questions]($boardChannel) or [#sitecie]($suggestionsChannel) on our Discord"
}

@Configuration
class SecurityContactsConfig {
    @Bean
    fun securityContacts(
        @Value($$"${app.security.contact-email}") email: String,
        @Value($$"${app.url}") apiUrl: String,
    ) = SecurityContacts(email, "$apiUrl/discord/channel/board", "$apiUrl/discord/channel/suggestions")
}
