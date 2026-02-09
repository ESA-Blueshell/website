package net.blueshell.api.auth.api

import net.blueshell.api.auth.security.JwtTokenUtil
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.auth.api.dto.request.JwtRequest
import net.blueshell.api.auth.api.dto.response.AuthenticationDTO
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.user.persistence.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.TestExecutionListeners.MergeMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NEVER)
@TestExecutionListeners(listeners = [], mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
class AuthenticationControllerIT @Autowired constructor(
    private val jwtTokenUtil: JwtTokenUtil
) {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var mapper: com.fasterxml.jackson.databind.ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userFactory: UserFactory

    @Value("\${app.jwt.expiration}")
    private var expiration: Long = 0

    private val rawPassword = "password123"

    @Test
    fun `authenticates valid credentials and returns jwt payload`() {
        val user = userRepository.save(userFactory.createWithRole(Role.MEMBER))
        val requestedAt = System.currentTimeMillis()

        val response = authenticate(user.username, rawPassword)
        val refreshed = userRepository.findById(user.id!!).orElseThrow()
        val expectedRoles = refreshed.inheritedRoles.sortedBy { it.ordinal }

        val expectedMin = requestedAt + expiration - 1_000
        val expectedMax = System.currentTimeMillis() + expiration + 1_000

        assertAll(
            { assertTrue(response.token.isNotBlank()) },
            { assertEquals(refreshed.id, response.userId) },
            { assertEquals(refreshed.username, response.username) },
            { assertEquals(expectedRoles, response.rolesSorted) },
            { assertTrue(response.expiration in expectedMin..expectedMax) },
            { assertTrue(jwtTokenUtil.validateToken(response.token, refreshed)) }
        )
    }

    @Test
    fun `fails authentication with wrong password`() {
        val user = userRepository.save(userFactory.createWithRole(Role.MEMBER))

        mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(JwtRequest(user.username, "WrongPassword!")))
        ).andExpect(status().isUnauthorized())
    }

    @Test
    fun `rejects disabled users`() {
        val disabledUser = userFactory.createWithRole(Role.MEMBER).apply { enabled = false }
        userRepository.save(disabledUser)

        mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(JwtRequest(disabledUser.username, rawPassword)))
        ).andExpect(status().isUnauthorized())
    }

    private fun authenticate(username: String, password: String): AuthenticationDTO {
        val result = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(JwtRequest(username, password)))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, AuthenticationDTO::class.java)
    }
}
