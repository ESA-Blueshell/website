package net.blueshell.api.domain.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for MemberProfileController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Users can manage their own member profile
 * - BOARD can manage profiles for any user
 * - Non-owners cannot access other profiles
 */
@SpringBootTest
class MemberProfileControllerSecurityTest : UserTestSupport() {

    private fun createPayload(userId: Long): String =
        """{"userId":$userId,"dateOfBirth":"1999-04-12","studentNumber":"s1234567","gender":"X","photoConsent":true,"nationality":"NL","bhv":true,"ehbo":false}"""

    private fun updatePayload(version: Long): String =
        """{"dateOfBirth":"2000-01-01","studentNumber":"s7654321","gender":"F","photoConsent":false,"nationality":"DE","bhv":false,"ehbo":true,"version":$version}"""

    @Nested
    inner class CreateMemberProfile {

        @Test
        fun `allows user to create member profile for self`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows BOARD to create member profile for any user`() {
            val board = createUserWithRole(Role.BOARD)
            val target = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(target.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies user from creating member profile for other user`() {
            val user = createUserWithRole(Role.MEMBER)
            val other = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(other.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateMemberProfile {

        @Test
        fun `allows user to update own member profile`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(user).memberProfile!!

            mvc.perform(
                put("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(profile.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to update any member profile`() {
            val board = createUserWithRole(Role.BOARD)
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(target).memberProfile!!

            mvc.perform(
                put("/users/{userId}/memberProfiles", target.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(profile.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from updating other user member profile`() {
            val user = createUserWithRole(Role.MEMBER)
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(target).memberProfile!!

            mvc.perform(
                put("/users/{userId}/memberProfiles", target.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(profile.version))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(target).memberProfile!!

            mvc.perform(
                put("/users/{userId}/memberProfiles", target.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(profile.version))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindMemberProfileByUserId {

        @Test
        fun `allows user to read own member profile`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                get("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to read any member profile`() {
            val board = createUserWithRole(Role.BOARD)
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                get("/users/{userId}/memberProfiles", target.id)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from reading other user member profile`() {
            val user = createUserWithRole(Role.MEMBER)
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                get("/users/{userId}/memberProfiles", target.id)
                    .with(bearer(user))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(get("/users/{userId}/memberProfiles", target.id))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val target = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                get("/users/{userId}/memberProfiles", target.id)
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }
    }
}
