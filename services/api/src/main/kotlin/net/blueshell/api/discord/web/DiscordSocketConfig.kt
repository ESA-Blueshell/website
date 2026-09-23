package net.blueshell.api.discord.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/** Serves [DiscordLiveSocket] beside `GET /discord/live`. */
@Configuration
@EnableWebSocket
class DiscordSocketConfig(
    private val socket: DiscordLiveSocket,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Any origin: the socket carries only what the server shows anybody, and reads no credential.
        registry.addHandler(socket, "/discord/live/socket").setAllowedOriginPatterns("*")
    }
}
