package net.blueshell.api.domain.committee.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class CommitteeControllerIT : UserTestSupport() {

    @Test
    fun `creates updates lists and deletes committees`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)

        val createResult = mvc.perform(
            post("/committees")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Integration Committee","description":"Committee description","members":[{"userId":${board.id},"role":"Chair"},{"userId":${member.id},"role":"Member"}]}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.members.length()").value(2))
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val committeeId = created.path("id").asLong()
        val version = created.path("version").asLong()

        assertThat(userRepository.findById(member.id!!).orElseThrow().roles).contains(Role.COMMITTEE)

        mvc.perform(get("/committees"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(committeeId))

        mvc.perform(get("/committees/{id}", committeeId).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(committeeId))
            .andExpect(jsonPath("$.members.length()").value(2))

        mvc.perform(
            put("/committees/{id}", committeeId)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Updated Committee","description":"Updated description","members":[{"userId":${board.id},"role":"Chair"}],"version":$version}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(committeeId))
            .andExpect(jsonPath("$.name").value("Updated Committee"))
            .andExpect(jsonPath("$.members.length()").value(1))

        assertThat(userRepository.findById(member.id!!).orElseThrow().roles).doesNotContain(Role.COMMITTEE)

        mvc.perform(delete("/committees/{id}", committeeId).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/committees/{id}", committeeId).with(bearer(board)))
            .andExpect(status().isNotFound)
    }
}
