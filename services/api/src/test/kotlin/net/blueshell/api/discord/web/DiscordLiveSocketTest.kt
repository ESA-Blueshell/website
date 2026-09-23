package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordLiveFeed
import net.blueshell.api.discord.domain.LiveRoom
import net.blueshell.api.discord.domain.VoicePerson
import net.blueshell.api.discord.domain.VoiceRoom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.databind.json.JsonMapper
import java.io.IOException

class DiscordLiveSocketTest {
    private val quiet = DiscordLive(server = "Blueshell", online = 269, members = 1199, rooms = emptyList())
    private val feed: DiscordLiveFeed = mock()
    private var stopped = 0
    private val stop: () -> Unit = { stopped++ }
    private val socket = DiscordLiveSocket(feed, JsonMapper.builder().build())

    private fun session(id: String): WebSocketSession =
        mock {
            on { this.id } doReturn id
            on { isOpen } doReturn true
        }

    private fun subscribed(): (DiscordLive?) -> Unit {
        val captor = argumentCaptor<(DiscordLive?) -> Unit>()
        doReturn(stop).whenever(feed).subscribe(captor.capture())
        socket.follow()
        return captor.firstValue
    }

    @Test
    fun `sends the server on connect, and again when it changes`() {
        val changed = subscribed()
        whenever(feed.current()).thenReturn(quiet)
        val page = session("a")

        socket.afterConnectionEstablished(page)
        changed(quiet.copy(online = 270))

        val sent = argumentCaptor<TextMessage>()
        verify(page, times(2)).sendMessage(sent.capture())
        assertThat(sent.firstValue.payload).contains("\"online\":269").contains("\"members\":1199")
        assertThat(sent.secondValue.payload).contains("\"online\":270")
    }

    @Test
    fun `turns a page away to try again later while there is no bot`() {
        whenever(feed.current()).thenReturn(null)
        val page = session("a")

        socket.afterConnectionEstablished(page)

        verify(page).close(CloseStatus.SERVICE_OVERLOAD)
        verify(page, never()).sendMessage(any())
    }

    @Test
    fun `closes every page when the bot drops out`() {
        val changed = subscribed()
        whenever(feed.current()).thenReturn(quiet)
        val page = session("a")
        socket.afterConnectionEstablished(page)

        changed(null)

        verify(page).close(CloseStatus.SERVICE_OVERLOAD)
    }

    @Test
    fun `stops sending to a page once it closed, or once a send to it failed`() {
        val changed = subscribed()
        whenever(feed.current()).thenReturn(quiet)
        val gone = session("a")
        val stuck = session("b")
        socket.afterConnectionEstablished(gone)
        socket.afterConnectionEstablished(stuck)
        socket.afterConnectionClosed(gone, CloseStatus.NORMAL)
        whenever(stuck.sendMessage(any())).doThrow(IOException("broken pipe"))

        changed(quiet.copy(online = 270))
        changed(quiet.copy(online = 271))

        verify(gone, times(1)).sendMessage(any())
        verify(stuck, times(2)).sendMessage(any())
        verify(stuck).close(CloseStatus.SESSION_NOT_RELIABLE)
    }

    @Test
    fun `sends each room with who is in it, as the REST endpoint does`() {
        subscribed()
        val room = VoiceRoom("3", "Members lounge", 1, locked = true, people = listOf(VoicePerson("Mo", "https://cdn/mo.png")))
        whenever(feed.current()).thenReturn(quiet.copy(rooms = listOf(LiveRoom(room, "https://discord.com/channels/324/3"))))
        val page = session("a")

        socket.afterConnectionEstablished(page)

        val sent = argumentCaptor<TextMessage>()
        verify(page).sendMessage(sent.capture())
        assertThat(sent.firstValue.payload)
            .contains("\"locked\":true")
            .contains("\"href\":\"https://discord.com/channels/324/3\"")
            .contains("\"avatar\":\"https://cdn/mo.png\"")
    }

    @Test
    fun `pings every open page, and stops following the feed when it goes`() {
        subscribed()
        whenever(feed.current()).thenReturn(quiet)
        val page = session("a")
        socket.afterConnectionEstablished(page)

        socket.ping()
        socket.unfollow()

        verify(page).sendMessage(isA<PingMessage>())
        assertThat(stopped).isOne()
    }
}
