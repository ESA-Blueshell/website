package net.blueshell.api.domain.telemetry.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for TelemetryController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Anyone can read telemetry
 * - BOARD users can create telemetry
 * - Non-BOARD users cannot create telemetry
 */
@SpringBootTest
class TelemetryControllerSecurityTest : UserTestSupport() {
    private fun telemetryPayload(url: String = "https://example.com/test"): String =
        """{"url":"$url","platform":"TWITTER"}"""

    @Nested
    inner class FindTelemetryById {

        @Test
        fun `allows anyone to read telemetry`() {
            val telemetryId = createTelemetryFixture().id!!

            mvc.perform(get("/telemetry/{id}", telemetryId))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to read telemetry`() {
            val member = createUserWithRole(Role.MEMBER)
            val telemetryId = createTelemetryFixture().id!!

            mvc.perform(
                get("/telemetry/{id}", telemetryId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to read telemetry`() {
            val board = createUserWithRole(Role.BOARD)
            val telemetryId = createTelemetryFixture().id!!

            mvc.perform(
                get("/telemetry/{id}", telemetryId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class CreateTelemetry {

        @Test
        fun `allows BOARD to create telemetry`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/telemetry")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryPayload())
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating telemetry`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/telemetry")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryPayload())
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/telemetry")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryPayload())
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                post("/telemetry")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryPayload())
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `COMMITTEE cannot create telemetry`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/telemetry")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telemetryPayload())
            )
                .andExpect(status().isForbidden)
        }
    }
}
