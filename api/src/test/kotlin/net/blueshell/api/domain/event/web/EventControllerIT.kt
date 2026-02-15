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
class EventControllerIT : UserTestSupport() {

    @Test
    fun `creates updates lists and deletes events`() {
        val board = createUserWithRole(Role.BOARD)
        val committee = createCommitteeFixture()

        val createBody =
            """{"committeeId":${committee.id},"title":"Integration Event","description":"Event description","location":"Campus","startTime":"2026-03-01T19:00:00Z","endTime":"2026-03-01T21:00:00Z","approved":true,"membersOnly":false,"signUp":true}"""

        val createResult = mvc.perform(
            post("/events")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.committeeId").value(committee.id))
            .andExpect(jsonPath("$.title").value("Integration Event"))
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val eventId = created.path("id").asLong()
        val version = created.path("version").asLong()

        mvc.perform(get("/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(eventId))

        mvc.perform(get("/events/{id}", eventId).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(eventId))

        val updateBody =
            """{"committeeId":${committee.id},"title":"Updated Integration Event","description":"Updated description","location":"Updated Campus","startTime":"2026-03-01T19:00:00Z","endTime":"2026-03-01T21:00:00Z","approved":false,"membersOnly":false,"signUp":false,"version":$version}"""

        mvc.perform(
            put("/events/{id}", eventId)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(eventId))
            .andExpect(jsonPath("$.title").value("Updated Integration Event"))
            .andExpect(jsonPath("$.signUp").value(false))

        mvc.perform(delete("/events/{eventId}", eventId).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/events/{id}", eventId).with(bearer(board)))
            .andExpect(status().isNotFound)
    }
}
