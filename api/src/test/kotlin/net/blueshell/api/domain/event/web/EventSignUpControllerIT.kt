package net.blueshell.api.domain.event.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class EventSignUpControllerIT : UserTestSupport() {

    @Test
    fun `member can create update list and delete signup`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

        val createResult = mvc.perform(
            post("/events/{eventId}/signups", event.id)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.eventId").value(event.id))
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val signUpId = created.path("id").asLong()
        val version = created.path("version").asLong()

        mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(signUpId))

        mvc.perform(
            put("/events/{eventId}/signups", event.id)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id},"version":$version}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(signUpId))

        mvc.perform(delete("/events/signups/{eventSignupId}", signUpId).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `guest can fetch update and delete signup with access token`() {
        val board = createUserWithRole(Role.BOARD)
        val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

        val createResult = mvc.perform(
            post("/events/{eventId}/signups", event.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"guest":{"name":"Guest User","discord":"guest#1234","email":"guest@example.com","phoneNumber":"+31612345678"}}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.guest.accessToken").isNotEmpty)
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val signUpId = created.path("id").asLong()
        val accessToken = created.path("guest").path("accessToken").asText()
        val version = created.path("version").asLong()

        mvc.perform(get("/events/signups/byAccessToken/{accessToken}", accessToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(signUpId))

        mvc.perform(
            put("/events/{eventId}/signups", event.id)
                .param("accessToken", accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"guest":{"name":"Guest Updated","discord":"guest#1234","email":"guest@example.com","phoneNumber":"+31612345678"},"version":$version}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(signUpId))

        mvc.perform(
            delete("/events/signups/{eventSignupId}", signUpId)
                .param("accessToken", accessToken)
        )
            .andExpect(status().isNoContent)
    }
}
