package net.blueshell.api.discord.web

import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class DiscordLiveIT : UserTestSupport() {
    /** The band sits on the front page, so a visitor reaches it; the test profile runs no bot, hence 503. */
    @Test
    fun `a visitor who is not logged in reaches the live endpoint`() {
        mvc.perform(get("/discord/live")).andExpect(status().isServiceUnavailable)
    }

    /** Refused by security, a socket would answer 401 before it ever reached the handler. */
    @Test
    fun `a visitor who is not logged in reaches the socket`() {
        mvc.perform(get("/discord/live/socket")).andExpect(status().isBadRequest)
    }

    /** Without a bot every link still works: it leads to the invite the site has always used. */
    @Test
    fun `a visitor follows a link into Discord, which falls back without a bot`() {
        mvc
            .perform(get("/discord/invite/board"))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "https://discord.gg/23YMFQy"))
        mvc.perform(get("/discord/channel/suggestions")).andExpect(status().isFound)
        mvc.perform(get("/discord/invite/lobby")).andExpect(status().isNotFound)
    }
}
