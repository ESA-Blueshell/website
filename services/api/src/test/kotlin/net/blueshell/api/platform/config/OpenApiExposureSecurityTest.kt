package net.blueshell.api.platform.config

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "security.openapi.public.enabled=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    ]
)
class OpenApiExposureRestrictedSecurityTest : UserTestSupport() {

    @Test
    fun `unauthenticated user cannot access api docs when public exposure is disabled`() {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `board user can access api docs when public exposure is disabled`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(get("/v3/api-docs").with(bearer(board)))
            .andExpect(status().isOk)
    }
}

@SpringBootTest(
    properties = [
        "security.openapi.public.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
    ]
)
class OpenApiExposurePublicSecurityTest : UserTestSupport() {

    @Test
    fun `unauthenticated user can access api docs when explicitly enabled`() {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
    }
}
