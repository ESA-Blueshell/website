package net.blueshell.api.domain.sponsor.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for SponsorController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can perform all sponsor operations (CRUD)
 * - Non-BOARD users cannot access any sponsor endpoints
 */
@SpringBootTest
class SponsorControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class FindSponsors {

        @Test
        fun `allows BOARD to list sponsors`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/sponsors")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing sponsors`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/sponsors")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/sponsors"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class CreateSponsor {

        @Test
        fun `allows BOARD to create sponsors`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/sponsors")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating sponsors`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/sponsors")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/sponsors")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"New Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateSponsor {

        @Test
        fun `allows BOARD to update sponsors`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsorId = 1L

            mvc.perform(
                put("/sponsors/{id}", sponsorId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating sponsors`() {
            val member = createUserWithRole(Role.MEMBER)
            val sponsorId = 1L

            mvc.perform(
                put("/sponsors/{id}", sponsorId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val sponsorId = 1L

            mvc.perform(
                put("/sponsors/{id}", sponsorId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Sponsor","logo":"https://example.com/logo.png"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindSponsorById {

        @Test
        fun `allows BOARD to read sponsor details`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsorId = 1L

            mvc.perform(
                get("/sponsors/{id}", sponsorId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from reading sponsors`() {
            val member = createUserWithRole(Role.MEMBER)
            val sponsorId = 1L

            mvc.perform(
                get("/sponsors/{id}", sponsorId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val sponsorId = 1L

            mvc.perform(get("/sponsors/{id}", sponsorId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteSponsor {

        @Test
        fun `allows BOARD to delete sponsors`() {
            val board = createUserWithRole(Role.BOARD)
            val sponsorId = 1L

            mvc.perform(
                delete("/sponsors/{id}", sponsorId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting sponsors`() {
            val member = createUserWithRole(Role.MEMBER)
            val sponsorId = 1L

            mvc.perform(
                delete("/sponsors/{id}", sponsorId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val sponsorId = 1L

            mvc.perform(delete("/sponsors/{id}", sponsorId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/sponsors")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot access sponsor endpoints`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/sponsors")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
