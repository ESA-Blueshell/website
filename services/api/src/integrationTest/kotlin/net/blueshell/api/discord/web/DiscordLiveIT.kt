package net.blueshell.api.discord.web

import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
}
