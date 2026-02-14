package net.blueshell.api.platform.integration.job.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for JobManagementController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - ADMIN role can list and retry jobs
 * - BOARD cannot access job endpoints
 * - Regular users cannot access job endpoints
 */
@SpringBootTest
class JobManagementControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class ListJobs {

        @Test
        fun `allows ADMIN to list jobs`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies BOARD from listing jobs`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from listing jobs`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from listing jobs`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/management/jobs"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RetryJob {

        @Test
        fun `allows ADMIN to retry jobs`() {
            val admin = createUserWithRole(Role.ADMIN)
            val jobId = 1L

            mvc.perform(
                post("/management/jobs/{id}/retry", jobId)
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies BOARD from retrying jobs`() {
            val board = createUserWithRole(Role.BOARD)
            val jobId = 1L

            mvc.perform(
                post("/management/jobs/{id}/retry", jobId)
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from retrying jobs`() {
            val member = createUserWithRole(Role.MEMBER)
            val jobId = 1L

            mvc.perform(
                post("/management/jobs/{id}/retry", jobId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val jobId = 1L

            mvc.perform(post("/management/jobs/{id}/retry", jobId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleExclusivity {

        @Test
        fun `COMMITTEE cannot access job endpoints even with BOARD-like permissions`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `ADMIN is the only role that can access jobs`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/management/jobs")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }
    }
}
