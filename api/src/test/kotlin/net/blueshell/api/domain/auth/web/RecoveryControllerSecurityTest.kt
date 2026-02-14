package net.blueshell.api.domain.auth.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for RecoveryController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Anyone can request password reset
 * - Anyone can activate account
 * - BOARD can resend member activation
 * - Non-BOARD cannot resend member activation
 */
@SpringBootTest
class RecoveryControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class ResetPassword {

        @Test
        fun `allows anyone to request password reset`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(post("/recovery/password/reset/{username}", user.username))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `returns 204 for non-existent user (security)`() {
            mvc.perform(post("/recovery/password/reset/{username}", "nonexistent"))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows authenticated user to request password reset`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/recovery/password/reset/{username}", user.username)
                    .with(bearer(user))
            )
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class SetPassword {

        @Test
        fun `allows anyone to set password with valid token`() {
            mvc.perform(
                post("/recovery/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123","password":"NewPassword123!"}""")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows authenticated user to set password`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/recovery/password")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123","password":"NewPassword123!"}""")
            )
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class UserActivate {

        @Test
        fun `allows anyone to activate user account`() {
            mvc.perform(
                post("/recovery/user/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to activate account`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/recovery/user/activate")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123"}""")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class MemberActivate {

        @Test
        fun `allows anyone to activate member account`() {
            mvc.perform(
                post("/recovery/member/activate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to activate as member`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/recovery/member/activate")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token":"validtoken123"}""")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class ResendUserActivation {

        @Test
        fun `allows anyone to resend user activation`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(post("/recovery/user/activate/resend/{username}", user.username))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `returns 204 for non-existent user (security)`() {
            mvc.perform(post("/recovery/user/activate/resend/{username}", "nonexistent"))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows authenticated user to resend activation`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/recovery/user/activate/resend/{username}", user.username)
                    .with(bearer(user))
            )
                .andExpect(status().isNoContent)
        }
    }

    @Nested
    inner class ResendMemberActivationEmail {

        @Test
        fun `allows BOARD to resend member activation`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", targetUser.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from resending member activation`() {
            val member = createUserWithRole(Role.MEMBER)
            val targetUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", targetUser.id)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from resending member activation`() {
            val guest = createUserWithRole(Role.GUEST)
            val targetUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", targetUser.id)
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val targetUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(post("/recovery/users/{userId}/resend/recovery", targetUser.id))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val targetUser = createUserWithRole(Role.MEMBER, enabled = false)

            mvc.perform(
                post("/recovery/users/{userId}/resend/recovery", targetUser.id)
                    .with(bearer(admin))
            )
                .andExpect(status().isNoContent)
        }
    }
}
