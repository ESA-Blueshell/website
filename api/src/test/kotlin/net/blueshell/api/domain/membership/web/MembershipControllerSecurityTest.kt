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
 * - MEMBER/BOARD users can create their own membership when eligible
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
        fun `denies MEMBER from creating a membership for self`() {
            val member = assignAddress(createUserWithRole(Role.MEMBER))

            mvc.perform(
                post("/memberships")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `allows a GUEST with an address to create a membership`() {
            val guest = assignAddress(createUserWithRole(Role.GUEST))

            mvc.perform(
                post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isCreated)
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
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users/${targetUser.id}/memberships")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from board creating membership`() {
            val member = createUserWithRole(Role.MEMBER)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users/${targetUser.id}/memberships")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from board creating membership`() {
            val guest = createUserWithRole(Role.GUEST)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users/${targetUser.id}/memberships")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/memberships/member")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":999999,"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateMembership {

        @Test
        fun `allows BOARD to update memberships`() {
            val board = createUserWithRole(Role.BOARD)
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating memberships`() {
            val member = createUserWithRole(Role.MEMBER)
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from updating memberships`() {
            val guest = createUserWithRole(Role.GUEST)
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                put("/memberships/{id}", membershipId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":999999,"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindMembershipById {

        @Test
        fun `allows user to read own membership`() {
            val user = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture(user = user).id!!

            mvc.perform(
                get("/memberships/{id}", membershipId)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to read any membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

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
            val membershipId = createMembershipFixture(user = user2).id!!

            mvc.perform(
                get("/memberships/{id}", membershipId)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture().id!!

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
