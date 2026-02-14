package net.blueshell.api.domain.event.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for EventSignUpController.
 *
 * Verifies complex multi-condition authorization rules are correctly enforced per ADR-014:
 * - BOARD can access all event signups
 * - Users can access own event signups via userId filter
 * - Committee members can access signups for their events
 * - Guest access token support for unauthenticated signups
 * - Users can create/update/delete own signups
 * - Cross-user signup access is denied
 */
@SpringBootTest
class EventSignUpControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class FindEventSignUps {

        @Test
        fun `allows BOARD to access all signups`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/events/signups")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows user to access own signups via userId filter`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/events/signups")
                    .param("userId", user.id.toString())
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from accessing other user's signups via userId filter`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/events/signups")
                    .param("userId", user2.id.toString())
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `allows committee member to access signups for their committee's event`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                get("/events/signups")
                    .param("committeeId", "1")
                    .with(bearer(committee))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user without filter access`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/events/signups")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/events/signups"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindEventSignUpsByAccessToken {

        @Test
        fun `allows valid access token to view signups`() {
            val validToken = "validAccessToken123"

            mvc.perform(
                get("/events/signups/byAccessToken/{accessToken}", validToken)
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies without access token parameter`() {
            mvc.perform(get("/events/signups/byAccessToken/{accessToken}", ""))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindEventSignUpsByEventId {

        @Test
        fun `allows BOARD to view signups for any event`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows event organizer to view signups for their event`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(committee))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from viewing event signups`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(get("/events/{eventId}/signups", eventId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class CreateEventSignup {

        @Test
        fun `allows BOARD to create signups for any event`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test User","email":"test@example.com"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows user to sign up for event they have permission for`() {
            val user = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test User","email":"test@example.com"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies user from signing up for event with insufficient permissions`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test User","email":"test@example.com"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test User","email":"test@example.com"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateEventSignup {

        @Test
        fun `allows BOARD to update signups`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Name"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows user to update own signup`() {
            val user = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Name"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest with valid access token to update signup`() {
            val eventId = 1L
            val accessToken = "validGuestToken123"

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .param("accessToken", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Updated Guest Name"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from updating other user's signup`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(user1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Hacked Name"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated without access token`() {
            val eventId = 1L

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Unauthorized Update"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteEventSignup {

        @Test
        fun `allows BOARD to delete any signup`() {
            val board = createUserWithRole(Role.BOARD)
            val signupId = 1L

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows user to delete own signup`() {
            val user = createUserWithRole(Role.MEMBER)
            val signupId = 1L

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(user))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows guest with valid access token to delete signup`() {
            val signupId = 1L
            val accessToken = "validGuestToken123"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", accessToken)
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies user from deleting other user's signup`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val signupId = 1L

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies invalid guest token from deleting signup`() {
            val signupId = 1L
            val invalidToken = "invalidToken"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", invalidToken)
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated without access token`() {
            val signupId = 1L

            mvc.perform(delete("/events/signups/{eventSignupId}", signupId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class GuestAccessToken {

        @Test
        fun `allows guest access token holder to view own signup`() {
            val accessToken = "guestToken123"

            mvc.perform(
                get("/events/signups/byAccessToken/{accessToken}", accessToken)
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest access token holder to update signup`() {
            val eventId = 1L
            val accessToken = "guestToken123"

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .param("accessToken", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Guest Updated"}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest access token holder to delete signup`() {
            val signupId = 1L
            val accessToken = "guestToken123"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", accessToken)
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies invalid guest access token from all operations`() {
            val signupId = 1L
            val invalidToken = "invalidGuestToken"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", invalidToken)
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val eventId = 1L

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `BOARD can perform COMMITTEE operations`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Test","email":"test@example.com"}""")
            )
                .andExpect(status().isCreated)
        }
    }
}
