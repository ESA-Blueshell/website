package net.blueshell.api.platform.integration.discord

import net.blueshell.clients.discord.api.DiscordApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Live smoke test for the generated Discord client. Calls `GET /users/@me` to
 * verify the bot token is accepted and the [DiscordClientConfig] wiring is
 * correct.
 *
 * Uses the "discord-live" profile, which deactivates the test/dev guards and
 * activates [DiscordClientConfig]. Credentials are resolved from the
 * environment (`DISCORD_BOT_TOKEN`).
 *
 *     docker compose run api \
 *       ./gradlew :services:api:discordLiveTest --tests "*.DiscordClientLiveIT"
 */
@Tag("discord-live")
@SpringBootTest
@ActiveProfiles("discord-live")
class DiscordClientLiveIT {

    @Autowired
    private lateinit var discordApi: DiscordApi

    @Test
    fun `bot token resolves the bot user`() {
        val me = discordApi.getMyUser()
        assertThat(me).isNotNull
        assertThat(me.id).isNotBlank()
    }
}
