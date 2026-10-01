package net.blueshell.api.discord.web

import net.blueshell.api.discord.domain.BotStanding
import net.blueshell.api.discord.domain.BotStandingResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordBotControllerTest {
    @Test
    fun `answers the bot's standing as the standing reads it`() {
        val standing = mock<BotStanding>()
        val read = BotStandingResult(true, true, true, null, emptyList(), emptyList())
        whenever(standing.read()).thenReturn(read)

        assertThat(DiscordBotController(standing).findBotStanding()).isSameAs(read)
    }
}
