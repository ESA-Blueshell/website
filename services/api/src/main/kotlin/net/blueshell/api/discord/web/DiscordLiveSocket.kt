package net.blueshell.api.discord.web

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordLiveFeed
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * The Discord band followed live: a socket that sends the band's server on connect and again
 * whenever it changes, as the JSON `GET /discord/live` answers. While the bot is not set up or not
 * connected it closes with 1013, try again later, and the band falls back to asking.
 *
 * Sends and nothing more: whatever a page writes is ignored.
 */
@Component
class DiscordLiveSocket(
    private val feed: DiscordLiveFeed,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()
    private var unsubscribe: () -> Unit = {}

    @PostConstruct
    fun follow() {
        unsubscribe = feed.subscribe(::broadcast)
    }

    @PreDestroy
    fun unfollow() = unsubscribe()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val guarded = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES)
        val live = feed.current()
        if (live == null) {
            guarded.close(CloseStatus.SERVICE_OVERLOAD)
            return
        }
        sessions += guarded
        send(guarded, messageOf(live))
    }

    override fun afterConnectionClosed(
        session: WebSocketSession,
        status: CloseStatus,
    ) {
        sessions.removeIf { it.id == session.id }
    }

    /* A proxy drops a connection that says nothing for long enough; a ping keeps each one open. */
    @Scheduled(fixedDelay = PING_EVERY_MS)
    fun ping() = sessions.forEach { session -> runCatching { session.sendMessage(PingMessage()) } }

    private fun broadcast(live: DiscordLive?) {
        if (live == null) {
            sessions.forEach { runCatching { it.close(CloseStatus.SERVICE_OVERLOAD) } }
            return
        }
        val message = messageOf(live)
        sessions.forEach { send(it, message) }
    }

    private fun messageOf(live: DiscordLive) = TextMessage(objectMapper.writeValueAsString(live.toResponse()))

    private fun send(
        session: WebSocketSession,
        message: TextMessage,
    ) {
        runCatching { session.sendMessage(message) }.onFailure {
            log.debug("Dropped a Discord socket that could not keep up", it)
            sessions -= session
            runCatching { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
        }
    }

    private companion object {
        const val SEND_TIME_LIMIT_MS = 5_000
        const val BUFFER_LIMIT_BYTES = 64 * 1024
        const val PING_EVERY_MS = 30_000L
        val log = LoggerFactory.getLogger(DiscordLiveSocket::class.java)
    }
}
