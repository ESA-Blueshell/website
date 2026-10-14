package net.blueshell.api.discord.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class DiscordBotSettingsIT : UserTestSupport() {
    @Test
    fun `the board sets where the bots post, a blank channel goes back to the configuration, and members cannot`() {
        val board = createUserWithRole(Role.BOARD)

        mvc
            .perform(get("/management/discord/settings/bot").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.infoChannel").value("events-info"))
        mvc
            .perform(
                put("/management/discord/settings/bot")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"infoChannel":"#news","calendarChannel":"","starboardChannel":"stars","claimRoleIds":["12"," 34 "]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.infoChannel").value("news"))
            .andExpect(jsonPath("$.calendarChannel").value("events-calendar"))
            .andExpect(jsonPath("$.claimRoleIds[1]").value("34"))
        mvc
            .perform(get("/management/discord/bot").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))

        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(get("/management/discord/settings/bot").with(signedIn(member))).andExpect(status().isForbidden)
        mvc.perform(get("/management/discord/bot").with(signedIn(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `refuses a channel name longer than Discord allows and a claim role that is not a Discord id`() {
        val board = createUserWithRole(Role.BOARD)
        val longName = "a".repeat(101)

        listOf(
            """{"infoChannel":"$longName","calendarChannel":"","starboardChannel":"","claimRoleIds":[]}""",
            """{"infoChannel":"","calendarChannel":"","starboardChannel":"","claimRoleIds":["Board"]}""",
            """{"infoChannel":"","calendarChannel":"","starboardChannel":"","claimRoleIds":[${(1..41).joinToString(",") { "\"$it\"" }}]}""",
        ).forEach { body ->
            mvc
                .perform(
                    put("/management/discord/settings/bot")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
        }
    }
}
