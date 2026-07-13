package net.blueshell.api.domain.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

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
                MockMvcRequestBuilders.get("/memberships")
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing memberships`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships")
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies GUEST from listing memberships`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(MockMvcRequestBuilders.get("/memberships"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class CreateMembership {

        @Test
        fun `denies MEMBER from creating a membership for self`() {
            val member = assignAddress(createUserWithRole(Role.MEMBER))

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships")
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `allows a GUEST with an address to create a membership`() {
            val guest = assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST)))

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
        }

        @Test
        fun `denies user with incomplete member application profile`() {
            val guest = assignAddress(createUserWithRole(Role.GUEST))

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies unauthenticated access`() {
            mvc.perform(MockMvcRequestBuilders.post("/memberships"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class BoardCreateMembership {

        @Test
        fun `allows BOARD to create membership for other users`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                MockMvcRequestBuilders.post("/users/${targetUser.id}/memberships")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from board creating membership`() {
            val member = createUserWithRole(Role.MEMBER)
            val targetUser = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                MockMvcRequestBuilders.post("/users/${targetUser.id}/memberships")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies GUEST from board creating membership`() {
            val guest = createUserWithRole(Role.GUEST)
            val targetUser = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                MockMvcRequestBuilders.post("/users/${targetUser.id}/memberships")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                MockMvcRequestBuilders.post("/memberships/member")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":999999,"memberType":"REGULAR","startDate":"2026-01-01","incasso":true}""")
            )
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
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
                MockMvcRequestBuilders.put("/memberships/{id}", membershipId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies non-BOARD users from updating memberships`() {
            val member = createUserWithRole(Role.MEMBER)
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}", membershipId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies GUEST from updating memberships`() {
            val guest = createUserWithRole(Role.GUEST)
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}", membershipId)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${membership.userId},"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membership = createMembershipFixture()
            val membershipId = membership.id!!

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}", membershipId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":999999,"memberType":"REGULAR","startDate":"2026-01-01","incasso":true,"version":${membership.version}}""")
            )
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class EndMembership {

        @Test
        fun `allows BOARD to end a membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships/{id}/end", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies non-BOARD users from ending a membership`() {
            val member = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships/{id}/end", membershipId)
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture().id!!

            mvc.perform(MockMvcRequestBuilders.post("/memberships/{id}/end", membershipId))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class ReopenMembership {

        @Test
        fun `allows BOARD to reopen a membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture(
                user = createUserWithRole(Role.GUEST),
                endDate = java.time.LocalDate.now().minusDays(1)
            ).id!!

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships/{id}/reopen", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies non-BOARD users from reopening a membership`() {
            val member = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture(
                endDate = java.time.LocalDate.now().minusDays(1)
            ).id!!

            mvc.perform(
                MockMvcRequestBuilders.post("/memberships/{id}/reopen", membershipId)
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture(
                endDate = java.time.LocalDate.now().minusDays(1)
            ).id!!

            mvc.perform(MockMvcRequestBuilders.post("/memberships/{id}/reopen", membershipId))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class FindMembershipById {

        @Test
        fun `allows user to read own membership`() {
            val user = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture(user = user).id!!

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships/{id}", membershipId)
                    .with(bearer(user))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `allows BOARD to read any membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies user from reading other user's membership`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture(user = user2).id!!

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships/{id}", membershipId)
                    .with(bearer(user1))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture().id!!

            mvc.perform(MockMvcRequestBuilders.get("/memberships/{id}", membershipId))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteMembership {

        @Test
        fun `allows BOARD to delete a membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting a membership`() {
            val member = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies GUEST from deleting a membership`() {
            val guest = createUserWithRole(Role.GUEST)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(guest))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val membershipId = createMembershipFixture().id!!

            mvc.perform(MockMvcRequestBuilders.delete("/memberships/{id}", membershipId))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class RestoreMembership {

        @Test
        fun `allows ADMIN to restore a membership`() {
            val admin = createUserWithRole(Role.ADMIN)
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}/restore", membershipId)
                    .with(bearer(admin))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies BOARD from restoring a membership`() {
            val admin = createUserWithRole(Role.ADMIN)
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}/restore", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies MEMBER from restoring a membership`() {
            val admin = createUserWithRole(Role.ADMIN)
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)

            mvc.perform(
                MockMvcRequestBuilders.put("/memberships/{id}/restore", membershipId)
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val admin = createUserWithRole(Role.ADMIN)
            val board = createUserWithRole(Role.BOARD)
            val membershipId = createMembershipFixture().id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)

            mvc.perform(MockMvcRequestBuilders.put("/memberships/{id}/restore", membershipId))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class FindDeletedMemberships {

        @Test
        fun `allows ADMIN to list deleted memberships`() {
            val admin = createUserWithRole(Role.ADMIN)
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val membershipId = createMembershipFixture(user = user).id!!

            mvc.perform(
                MockMvcRequestBuilders.delete("/memberships/{id}", membershipId)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isNoContent)

            mvc.perform(
                MockMvcRequestBuilders.get("/users/{userId}/memberships/deleted", user.id)
                    .with(bearer(admin))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `denies BOARD from listing deleted memberships`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                MockMvcRequestBuilders.get("/users/{userId}/memberships/deleted", user.id)
                    .with(bearer(board))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `denies MEMBER from listing deleted memberships`() {
            val member = createUserWithRole(Role.MEMBER)
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                MockMvcRequestBuilders.get("/users/{userId}/memberships/deleted", user.id)
                    .with(bearer(member))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(MockMvcRequestBuilders.get("/users/{userId}/memberships/deleted", user.id))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships")
                    .with(bearer(admin))
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
        }

        @Test
        fun `COMMITTEE cannot list memberships (not BOARD)`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                MockMvcRequestBuilders.get("/memberships")
                    .with(bearer(committee))
            )
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }
    }
}