package net.blueshell.api.domain.user.web

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
class UserControllerIT : UserTestSupport() {

    @Test
    fun `creates and updates user profile as board`() {
        val board = createUserWithRole(Role.BOARD)

        val createResult = mvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"username":"integration_user_${System.currentTimeMillis()}","initials":"IU","firstName":"Integration","lastName":"User","newsletter":true,"password":"Password123!","email":"integration_user_${System.currentTimeMillis()}@example.com","discord":"integration#1234","phoneNumber":"+31612345678"}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val userId = created.path("id").asLong()
        val username = created.path("username").asText()
        val email = created.path("email").asText()
        val version = created.path("version").asLong()

        mvc.perform(
            put("/users/{id}", userId)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"username":"$username","initials":"IU","firstName":"Updated","lastName":"User","newsletter":false,"nationality":"Dutch","email":"$email","discord":"updated#1234","phoneNumber":"+31612345679","version":$version}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.firstName").value("Updated"))
            .andExpect(jsonPath("$.discord").value("updated#1234"))
    }

    @Test
    fun `creates and updates guest user`() {
        val guestUsername = "guest_it_${System.currentTimeMillis()}"
        val guestEmail = "$guestUsername@example.com"

        val createResult = mvc.perform(
            post("/users/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"username":"$guestUsername","initials":"GU","firstName":"Guest","lastName":"User","newsletter":true,"password":"Password123!","email":"$guestEmail","discord":"guest#1234","phoneNumber":"+31612345678"}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val userId = created.path("id").asLong()
        val version = created.path("version").asLong()

        mvc.perform(
            put("/users/guest/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":$version}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.discord").value("guest_updated#1234"))
            .andExpect(jsonPath("$.phoneNumber").value("+31612345679"))
    }

    @Test
    fun `board can list and delete users`() {
        val board = createUserWithRole(Role.BOARD)
        val createdUser = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/users").with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mvc.perform(delete("/users/{userId}", createdUser.id).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/users/{userId}", createdUser.id).with(bearer(board)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `admin can toggle user role`() {
        val admin = createUserWithRole(Role.ADMIN)
        val createdUser = createUserWithRole(Role.GUEST)

        mvc.perform(
            put("/users/{userId}/roles", createdUser.id)
                .param("role", "MEMBER")
                .with(bearer(admin))
        )
            .andExpect(status().isOk)

        assertThat(userRepository.findById(createdUser.id!!).orElseThrow().roles).contains(Role.MEMBER)
    }
}
