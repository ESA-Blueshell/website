package net.blueshell.api.controller

import net.blueshell.api.common.enums.Role
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.model.User
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class CommitteeControllerIT @Autowired constructor(
    private val committeeDTOFactory: AdvancedCommitteeDTOFactory
) : UserTestSupport() {

    @Test
    fun `creates committees and assigns committee role`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val payload = committeePayload(board, member)

        mvc.perform(
            post("/committees")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value(payload.name))
            .andExpect(jsonPath("$.description").value(payload.description))
            .andExpect(jsonPath("$.members", hasSize<Any>(2)))
            .andExpect(jsonPath("$.members[*].role", containsInAnyOrder<Any>("Chair", "Member")))
            .andExpect(
                jsonPath(
                    "$.members[*].userId",
                    containsInAnyOrder<Any>(board.id!!.toInt(), member.id!!.toInt())
                )
            )

        assertThat(refreshUser(member).hasRole(Role.COMMITTEE)).isTrue()
        assertThat(refreshUser(board).hasRole(Role.COMMITTEE)).isTrue()
    }

    @Test
    fun `lists committees for board`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        createCommittee(board, member)

        mvc.perform(get("/committees").with(bearer(board)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Test
    fun `finds committee by id`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val created = createCommittee(board, member)

        mvc.perform(get("/committees/{id}", created.id).with(bearer(board)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id!!.toInt()))
            .andExpect(jsonPath("$.name").value(created.name))
            .andExpect(jsonPath("$.members", hasSize<Any>(2)))
    }

    @Test
    fun `updates committees and removes members`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val created = createCommittee(board, member)

        created.members.removeAt(1)
        created.name = "Updated Committee Name"
        created.description = "Updated description text"
        created.members.firstOrNull()?.role = "No longer chair"

        mvc.perform(
            put("/committees/{id}", created.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(created))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id!!.toInt()))
            .andExpect(jsonPath("$.name").value("Updated Committee Name"))
            .andExpect(jsonPath("$.description").value("Updated description text"))
            .andExpect(jsonPath("$.members", hasSize<Any>(1)))
            .andExpect(jsonPath("$.members[0].role").value("No longer chair"))
            .andExpect(jsonPath("$.members[0].userId").value(board.id!!.toInt()))

        assertThat(refreshUser(member).hasRole(Role.COMMITTEE)).isFalse()
        assertThat(refreshUser(board).hasRole(Role.COMMITTEE)).isTrue()
    }

    @Test
    fun `updates committees and member roles`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val created = createCommittee(board, member)

        created.name = "Updated Committee Name"
        created.description = "Updated description text"
        created.members[0].role = "No longer chair"
        created.members[1].role = "No longer member"

        mvc.perform(
            put("/committees/{id}", created.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(created))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Committee Name"))
            .andExpect(jsonPath("$.members", hasSize<Any>(2)))
            .andExpect(jsonPath("$.members[0].userId").value(board.id!!.toInt()))
            .andExpect(jsonPath("$.members[1].userId").value(member.id!!.toInt()))

        assertThat(refreshUser(board).hasRole(Role.COMMITTEE)).isTrue()
    }

    @Test
    fun `deletes committees`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val created = createCommittee(board, member)

        mvc.perform(delete("/committees/{id}", created.id).with(bearer(board)))
            .andExpect(status().isNoContent())

        mvc.perform(get("/committees").with(bearer(board)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        mvc.perform(get("/committees/{id}", created.id).with(bearer(board)))
            .andExpect(status().isNotFound())
    }

    private fun createCommittee(board: User, member: User): AdvancedCommitteeDTO {
        val payload = committeePayload(board, member)
        val result = mvc.perform(
            post("/committees")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, AdvancedCommitteeDTO::class.java)
    }

    private fun committeePayload(board: User, member: User): AdvancedCommitteeDTO {
        val dto = committeeDTOFactory.createWithMemberRoles("Chair", "Member")
        dto.name = "Test Committee"
        dto.description = "A test committee for integration tests"
        dto.members[0].userId = board.id
        dto.members[1].userId = member.id
        return dto
    }
}
