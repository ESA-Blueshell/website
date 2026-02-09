package net.blueshell.api.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import net.blueshell.api.auth.web.dto.request.JwtRequest
import net.blueshell.api.auth.web.dto.response.AuthenticationDTO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Base for tests that require an authenticated Bearer token.
 * The admin token is lazily fetched and cached per-JVM to speed up suites.
 */
@Component
abstract class JwtTestSupport {

    @Autowired
    protected lateinit var mvc: MockMvc

    @Autowired
    protected lateinit var mapper: ObjectMapper

    /**
     * Obtain (and cache) a JWT for the seeded admin/admin account.
     */
    protected fun adminToken(): String {
        cachedAdminToken?.let { return it }

        val requestBody = JwtRequest("admin", "admin")

        val result = mvc.perform(
            post("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(requestBody))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andReturn()

        val response = mapper.readValue(result.response.contentAsByteArray, AuthenticationDTO::class.java)
        cachedAdminToken = response.token
        return response.token
    }

    /**
     * Convenience wrapper to apply Authorization: Bearer <token>.
     */
    protected fun bearer(): RequestPostProcessor {
        val token = adminToken()
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }

    private companion object {
        var cachedAdminToken: String? = null
    }
}
