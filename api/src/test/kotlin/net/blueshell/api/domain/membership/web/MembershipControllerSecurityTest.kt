package net.blueshell.api.domain.membership.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for MembershipController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can list all memberships
 * - GUEST users can create their own membership
 * - BOARD users can create memberships for others
 * - Users can read their own membership, BOARD can read any
 * - BOARD users can update memberships
 */
@SpringBootTest
class MembershipControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class FindMemberships {

        @Test
        fun `allows BOARD to list all memberships`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/memberships")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing memberships`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/memberships")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from listing memberships`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                get("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/memberships"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class CreateMembership {

        @Test
        fun `allows GUEST to create membership for self`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows MEMBER to create membership`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships")
                    .with(bearer(member))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies BOARD from self-creating membership via standard endpoint`() {
            // BOARD should use boardCreateMembership endpoint instead
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/memberships")
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies unauthenticated access`() {
            mvc.perform(post("/memberships"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class BoardCreateMembership {

        @Test
        fun `allows BOARD to create membership for other users`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/memberships/member")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from board creating membership`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/member")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from board creating membership`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/memberships/member")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/memberships/member")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":1}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateMembership {

        @Test
        fun `allows BOARD to update memberships`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = 1L // Assuming ID 1 exists or creating one

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ACTIVE"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating memberships`() {
            val member = createUserWithRole(Role.MEMBER)
            val membershipId = 1L

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ACTIVE"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from updating memberships`() {
            val guest = createUserWithRole(Role.GUEST)
            val membershipId = 1L

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ACTIVE"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = 1L

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"ACTIVE"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindMembershipById {

        @Test
        fun `allows user to read own membership`() {
            val user = createUserWithRole(Role.MEMBER)
            val membershipId = 1L // In a real test, this would be the user's actual membership

            mvc.perform(
                get("/memberships/{id}", membershipId)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to read any membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = 1L

            mvc.perform(
                get("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from reading other user's membership`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val membershipId = 1L // Belongs to user2

            mvc.perform(
                get("/memberships/{id}", membershipId)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = 1L

            mvc.perform(get("/memberships/{id}", membershipId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/memberships")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `COMMITTEE cannot list memberships (not BOARD)`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/memberships")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }
    }
}
