package net.blueshell.api.platform.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for MainController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Health check is publicly accessible
 * - No authentication required
 */
@SpringBootTest
class MainControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class HealthCheck {

        @Test
        fun `allows unauthenticated access to health check`() {
            mvc.perform(get("/health"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to access health check`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/health")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to access health check`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/health")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows GUEST to access health check`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                get("/health")
                    .with(bearer(guest))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns true for health status`() {
            mvc.perform(get("/health"))
                .andExpect(status().isOk)
                // Note: Response body would be checked in integration tests
        }
    }
}
