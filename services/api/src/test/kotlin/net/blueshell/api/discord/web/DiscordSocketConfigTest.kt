package net.blueshell.api.discord.web

import net.blueshell.api.discord.ModuleMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.modulith.ApplicationModule
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

class DiscordSocketConfigTest {
    @Test
    fun `serves the socket beside the REST read, to any origin`() {
        val socket: DiscordLiveSocket = mock()
        val registration: WebSocketHandlerRegistration = mock()
        val registry: WebSocketHandlerRegistry = mock { on { addHandler(any(), any()) } doReturn registration }

        DiscordSocketConfig(socket).registerWebSocketHandlers(registry)

        verify(registry).addHandler(eq(socket), eq("/discord/live/socket"))
        verify(registration).setAllowedOriginPatterns("*")
    }

    @Test
    fun `the discord module reaches only the shared kernel, and the port sync declares for it`() {
        val module = ModuleMetadata()::class.java.getAnnotation(ApplicationModule::class.java)

        assertThat(module.allowedDependencies).containsExactly("shared", "sync :: api")
    }
}
