package net.blueshell.api.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.auth.web.dto.request.JwtRequest
import net.blueshell.api.auth.web.dto.response.AuthenticationDTO
import net.blueshell.api.platform.config.TruncateTestDatabaseListener
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

/**
 * Test utilities for creating users with specific roles and issuing their JWT tokens.
 */
@TestExecutionListeners(
    listeners = [TruncateTestDatabaseListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
abstract class UserTestSupport {

    @Autowired
    protected lateinit var mvc: MockMvc

    @Autowired
    protected lateinit var mapper: ObjectMapper

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var passwordEncoder: PasswordEncoder

    /**
     * Persist a new enabled user with the given role.
     */
    protected fun createUserWithRole(role: Role): User {
        val username = "${role.name.lowercase()}_${UUID.randomUUID().toString().substring(0, 8)}"
        val user = User()
        user.firstName = username
        user.lastName = username
        user.username = username
        user.password = passwordEncoder.encode(DEFAULT_PASSWORD)
        user.email = "$username@example.com"
        user.enabled = true
        user.roles = role.allInheritedRoles
        return userRepository.save(user)
    }

    /**
     * Obtain a JWT token for a fresh user in the given role.
     */
    protected fun tokenForRole(role: Role): String {
        val user = createUserWithRole(role)
        return tokenForUser(user)
    }

    /**
     * Obtain a JWT token for the given user.
     */
    protected fun tokenForUser(user: User): String {
        val requestBody = JwtRequest(user.username, DEFAULT_PASSWORD)
        val result = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(requestBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()
        val response = mapper.readValue(result.response.contentAsByteArray, AuthenticationDTO::class.java)
        return response.token
    }

    /**
     * Convenience to use: .with(bearer(role))
     */
    protected fun bearer(role: Role): RequestPostProcessor {
        val token = tokenForRole(role)
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }

    /**
     * Convenience to use: .with(bearer(user))
     */
    protected fun bearer(user: User): RequestPostProcessor {
        val token = tokenForUser(user)
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }

    /**
     * Set the Spring SecurityContext to a user with the given role (non-MVC tests).
     */
    protected fun setAuthenticationWithRole(role: Role) {
        val user = createUserWithRole(role)
        val auth: Authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        SecurityContextHolder.getContext().authentication = auth
    }

    /**
     * Reload a user from the repository.
     */
    protected fun refreshUser(user: User): User {
        return userRepository.findById(user.id!!).orElseThrow()
    }

    private companion object {
        const val DEFAULT_PASSWORD = "Password123!"
    }
}
