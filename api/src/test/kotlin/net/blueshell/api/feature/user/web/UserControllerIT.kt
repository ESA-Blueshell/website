package net.blueshell.api.feature.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.feature.user.dto.AdvancedUserDTO
import net.blueshell.api.feature.user.dto.SimpleUserDTO
import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIT @Autowired constructor(
    private val advancedUserDTOFactory: AdvancedUserDTOFactory,
    private val simpleUserDTOFactory: SimpleUserDTOFactory
) : UserTestSupport() {

    @Test
    fun `creates users via advanced endpoint`() {
        val payload = advancedUserDTOFactory.createBasic()

        val response = createUser(payload)

        assertAll(
            { assertNotNull(response.id) },
            { assertEquals(payload.username, response.username) },
            { assertEquals(payload.email, response.email) },
        )
    }

    @Test
    fun `creates guest users via simple endpoint`() {
        val payload = simpleUserDTOFactory.createBasic()

        val response = createGuest(payload)

        assertAll(
            { assertNotNull(response.id) },
            { assertEquals(payload.username, response.username) },
            { assertEquals(payload.email, response.email) },
        )
    }

    @Test
    fun `updates guest user contact fields`() {
        val created = createGuest(simpleUserDTOFactory.createBasic())
        val guest = userRepository.findById(created.id!!).orElseThrow()
        val updatePayload = simpleUserDTOFactory.createBasic().apply {
            id = created.id
            username = created.username
            email = created.email
            initials = created.initials
            firstName = created.firstName
            lastName = created.lastName
            newsletter = !created.newsletter
            discord = "${created.username}-updated"
            phoneNumber = "+31612345679"
        }

        guest.enabled = true
        userRepository.save(guest)

        val result = mvc.perform(
            put("/users/guest/{id}", created.id)
                .with(bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(updatePayload))
        )
            .andExpect(status().isOk())
            .andReturn()

        val response = mapper.readValue(result.response.contentAsByteArray, SimpleUserDTO::class.java)

        assertAll(
            { assertEquals(created.id, response.id) },
            { assertEquals(updatePayload.discord, response.discord) },
            { assertEquals(updatePayload.phoneNumber, response.phoneNumber) },
            { assertEquals(updatePayload.newsletter, response.newsletter) },
        )
    }

    @Test
    fun `updates user profile as board`() {
        val created = createUser(advancedUserDTOFactory.createBasic())
        val board = createUserWithRole(Role.BOARD)

        val updatePayload = advancedUserDTOFactory.createBasic().apply {
            id = created.id
        }

        val result = mvc.perform(
            put("/users/{id}", created.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(updatePayload))
        )
            .andExpect(status().isOk())
            .andReturn()

        val response = mapper.readValue(result.response.contentAsByteArray, AdvancedUserDTO::class.java)

        assertAll(
            { assertEquals(created.id, response.id) },
            { assertEquals(updatePayload.username, response.username) },
            { assertEquals(updatePayload.email, response.email) },
            { assertEquals(updatePayload.discord, response.discord) },
            { assertEquals(updatePayload.nationality, response.nationality) },
        )
    }

    @Test
    fun `lists users for board`() {
        val created = createUser(advancedUserDTOFactory.createBasic())
        val board = createUserWithRole(Role.BOARD)

        val result = mvc.perform(get("/users").with(bearer(board)))
            .andExpect(status().isOk())
            .andReturn()

        val root = mapper.readTree(result.response.contentAsByteArray)
        val found = root.path("content").any { node -> node.path("id").asLong() == created.id }

        assertTrue(found)
    }

    @Test
    fun `finds user by id for board`() {
        val created = createUser(advancedUserDTOFactory.createBasic())
        val board = createUserWithRole(Role.BOARD)

        val result = mvc.perform(get("/users/{id}", created.id).with(bearer(board)))
            .andExpect(status().isOk())
            .andReturn()

        val response = mapper.readValue(result.response.contentAsByteArray, AdvancedUserDTO::class.java)

        assertAll(
            { assertEquals(created.id, response.id) },
            { assertEquals(created.username, response.username) },
        )
    }

    @Test
    fun `deletes users by id for board`() {
        val created = createUser(advancedUserDTOFactory.createBasic())
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(delete("/users/{id}", created.id).with(bearer(board)))
            .andExpect(status().isNoContent())

        mvc.perform(get("/users/{id}", created.id).with(bearer(board)))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `toggles user roles as admin`() {
        val created = createUser(advancedUserDTOFactory.createBasic())
        val admin = createUserWithRole(Role.ADMIN)

        val result = mvc.perform(
            put("/users/{id}/roles", created.id)
                .with(bearer(admin))
                .param("role", Role.MEMBER.name)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id!!.toInt()))
            .andReturn()

        val response = mapper.readValue(result.response.contentAsByteArray, AdvancedUserDTO::class.java)

        assertTrue(response.rolesSorted.contains(Role.MEMBER))
    }

    private fun createUser(payload: AdvancedUserDTO): AdvancedUserDTO {
        val result = mvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, AdvancedUserDTO::class.java)
    }

    private fun createGuest(payload: SimpleUserDTO): SimpleUserDTO {
        val result = mvc.perform(
            post("/users/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, SimpleUserDTO::class.java)
    }
}
