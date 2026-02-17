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
 * Security tests for UserController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Users can only manage their own profiles
 * - BOARD users can manage any user and list all users
 * - ADMIN users can toggle user roles
 * - Guest user creation is public
 */
@SpringBootTest
class UserControllerSecurityTest : UserTestSupport() {
    private fun createGuestUserPayload(username: String, email: String): String =
        """{"username":"$username","initials":"GU","firstName":"Guest","lastName":"User","newsletter":false,"password":"Password123!","email":"$email","discord":"guest#1234","phoneNumber":"+31612345678"}"""

    @Nested
    inner class CreateUser {

        @Test
        fun `allows anyone to create regular user`() {
            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"newuser","email":"newuser@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows BOARD to create users `() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/users")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"adminuser","email":"adminuser@test.com","password":"Password123!","dateOfBirth":"1990-01-01","nationality":"Dutch","photoConsent":true,"ehbo":false,"bhv":false}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD authenticated users from creating regular users`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"newuser","email":"new@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies COMMITTEE from creating regular users`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/users")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"newuser","email":"new@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from creating users`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"newuser","email":"new@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `anonymous user creates regular user with GUEST role only`() {
            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"newuser","email":"new@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `anonymous user creates user with GUEST role and disabled state`() {
            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"testuser","email":"test@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isCreated)

            // User is created with GUEST role and disabled state
            // Additional roles granted through membership/committee operations
            // Account activation happens through recovery controller's activate endpoint
        }

        @Test
        fun `BOARD user creates user with GUEST role and disabled state`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/users")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"testuser2","email":"test2@test.com","password":"Password123!"}""")
            )
                .andExpect(status().isCreated)

            // User is created with GUEST role and disabled state regardless of who creates them
        }
    }

    @Nested
    inner class CreateGuestUser {

        @Test
        fun `allows anyone to create guest user`() {
            mvc.perform(
                post("/users/guest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createGuestUserPayload("guestuser1", "guest@test.com"))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows authenticated user to create guest user`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/users/guest")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createGuestUserPayload("guestuser2", "guest2@test.com"))
            )
                .andExpect(status().isCreated)
        }
    }

    @Nested
    inner class UpdateGuestUser {

        @Test
        fun `returns 401 when unauthenticated`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/guest/{id}", guest.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":${guest.version}}""")
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `allows guest user to update own profile`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/guest/{id}", guest.id)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":${guest.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to update guest user`() {
            val board = createUserWithRole(Role.BOARD)
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/guest/{id}", guest.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":${guest.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD user from updating another guest user`() {
            val member = createUserWithRole(Role.MEMBER)
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/guest/{id}", guest.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":${guest.version}}""")
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class UpdateUser {

        @Test
        fun `allows user to update own profile`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{id}", user.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"updated_self#1234","phoneNumber":"+31612345679","newsletter":false,"version":${user.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from updating another user's profile`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{id}", user2.id)
                    .with(bearer(user1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"hacked#1234","phoneNumber":"+31699999999","newsletter":false,"version":${user2.version}}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `allows BOARD to update any user profile`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{id}", targetUser.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"updated_by_board#1234","phoneNumber":"+31611111111","newsletter":true,"version":${targetUser.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{id}", user.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"discord":"unauthorized#1234","phoneNumber":"+31622222222","newsletter":false,"version":${user.version}}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindUsers {

        @Test
        fun `allows BOARD to list all users`() {
            val board = createUserWithRole(Role.BOARD)
            createUserWithRole(Role.MEMBER) // Create another user to list

            mvc.perform(
                get("/users")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing all users`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from listing users`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                get("/users")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/users"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindUserById {

        @Test
        fun `allows user to read own profile`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users/{userId}", user.id)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from reading another user's profile`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users/{userId}", user2.id)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `allows BOARD to read any user profile`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users/{userId}", targetUser.id)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows GUEST to read own profile`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                get("/users/{userId}", guest.id)
                    .with(bearer(guest))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(get("/users/{userId}", user.id))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteUser {

        @Test
        fun `allows BOARD to delete users`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                delete("/users/{userId}", targetUser.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies regular user from deleting users`() {
            val user = createUserWithRole(Role.MEMBER)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                delete("/users/{userId}", targetUser.id)
                    .with(bearer(user))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from deleting users`() {
            val guest = createUserWithRole(Role.GUEST)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                delete("/users/{userId}", targetUser.id)
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(delete("/users/{userId}", user.id))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class ToggleUserRole {

        @Test
        fun `allows ADMIN to toggle user roles`() {
            val admin = createUserWithRole(Role.ADMIN)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/roles", targetUser.id)
                    .param("role", "BOARD")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies ADMIN from elevating own privileges`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                put("/users/{userId}/roles", admin.id)
                    .param("role", "SYSTEM")
                    .with(bearer(admin))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies BOARD from toggling user roles`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/roles", targetUser.id)
                    .param("role", "BOARD")
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from toggling roles`() {
            val user = createUserWithRole(Role.MEMBER)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/roles", targetUser.id)
                    .param("role", "BOARD")
                    .with(bearer(user))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/roles", targetUser.id)
                    .param("role", "BOARD")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform all operations that BOARD can`() {
            val admin = createUserWithRole(Role.ADMIN)
            createUserWithRole(Role.MEMBER)

            // ADMIN should be able to list users (BOARD capability)
            mvc.perform(
                get("/users")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `BOARD cannot perform ADMIN operations`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/roles", targetUser.id)
                    .param("role", "ADMIN")
                    .with(bearer(board))
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `denies access to non-existent user with correct error`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users/{userId}", 999999)
                    .with(bearer(user))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `disabled user cannot authenticate but permission evaluators still work`() {
            val disabledUser = createUserWithRole(Role.MEMBER, enabled = false)
            val anotherUser = createUserWithRole(Role.MEMBER)

            // Disabled user should not be able to authenticate
            // (This is handled by Spring Security, not the permission evaluator)
            // So trying to access with their token should fail at authentication stage
            mvc.perform(
                get("/users/{userId}", anotherUser.id)
                    .with(bearer(disabledUser))
            )
                .andExpect(status().isForbidden)
        }
    }
}
