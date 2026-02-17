package net.blueshell.api.domain.event.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

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
    private fun createSignUpGuestPayload(): String =
        """{"guest":{"name":"Test User","discord":"test#1234","email":"test@example.com","phoneNumber":"+31612345678"}}"""

    private fun updateSignUpGuestPayload(version: Long, name: String = "Updated Guest"): String =
        """{"guest":{"name":"$name","discord":"test#1234","email":"test@example.com","phoneNumber":"+31612345678"},"version":$version}"""

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
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)

            mvc.perform(
                get("/events/signups")
                    .param("committeeId", committee.id.toString())
                    .with(bearer(committeeUser))
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
            val guest = createGuestFixture()
            createEventSignUpFixture(event = createEventFixture(), user = null, guest = guest)

            mvc.perform(
                get("/events/signups/byAccessToken/{accessToken}", guest.accessToken)
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
            val eventId = createEventFixture().id!!

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows event organizer to view signups for their event`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)
            val eventId = createEventFixture(committee = committee).id!!

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(committeeUser))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from viewing event signups`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture().id!!

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = createEventFixture().id!!

            mvc.perform(get("/events/{eventId}/signups", eventId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class CreateEventSignup {

        @Test
        fun `allows BOARD to create signups for any event`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = createEventFixture().id!!

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSignUpGuestPayload())
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows user to sign up for event they have permission for`() {
            val user = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture(approved = true, membersOnly = false, signUp = true).id!!

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSignUpGuestPayload())
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies user from signing up for event with insufficient permissions`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture(approved = false, membersOnly = false, signUp = true).id!!

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSignUpGuestPayload())
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = createEventFixture().id!!

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSignUpGuestPayload())
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateEventSignup {

        @Test
        fun `allows BOARD to update signups`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture()
            val eventId = event.id!!
            val signUp = createEventSignUpFixture(event = event, user = board)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows user to update own signup`() {
            val user = createUserWithRole(Role.MEMBER)
            val event = createEventFixture()
            val eventId = event.id!!
            val signUp = createEventSignUpFixture(event = event, user = user)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest with valid access token to update signup`() {
            val event = createEventFixture()
            val eventId = event.id!!
            val guest = createGuestFixture(accessToken = "validGuestToken123")
            val signUp = createEventSignUpFixture(event = event, user = null, guest = guest)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .param("accessToken", guest.accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version, "Updated Guest Name"))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from updating other user's signup`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val event = createEventFixture()
            val eventId = event.id!!
            val signUp = createEventSignUpFixture(event = event, user = user2)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(user1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version, "Hacked Name"))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns 401 when unauthenticated without access token`() {
            val eventId = createEventFixture(approved = false, signUp = false).id!!

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(0L, "Unauthorized Update"))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `denies user from updating own signup after event has ended`() {
            val user = createUserWithRole(Role.MEMBER)
            val event = createEventFixture().apply {
                endTime = Instant.now().minusSeconds(60)
            }
            persist(event)
            val eventId = event.id!!
            val signUp = createEventSignUpFixture(event = event, user = user)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version, "Late Update"))
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class DeleteEventSignup {

        @Test
        fun `allows BOARD to delete any signup`() {
            val board = createUserWithRole(Role.BOARD)
            val signupId = createEventSignUpFixture(
                event = createEventFixture(),
                user = createUserWithRole(Role.MEMBER)
            ).id!!

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows user to delete own signup`() {
            val user = createUserWithRole(Role.MEMBER)
            val signupId = createEventSignUpFixture(event = createEventFixture(), user = user).id!!

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(user))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `allows guest with valid access token to delete signup`() {
            val guest = createGuestFixture(accessToken = "validGuestToken123")
            val signupId = createEventSignUpFixture(event = createEventFixture(), user = null, guest = guest).id!!

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", guest.accessToken)
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies user from deleting other user's signup`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val signupId = createEventSignUpFixture(event = createEventFixture(), user = user2).id!!

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies invalid guest token from deleting signup`() {
            val signupId = createEventSignUpFixture(
                event = createEventFixture(),
                user = null,
                guest = createGuestFixture(accessToken = "validGuestToken")
            ).id!!
            val invalidToken = "invalidToken"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", invalidToken)
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns 401 when unauthenticated without access token`() {
            val signupId = createEventSignUpFixture(
                event = createEventFixture(),
                user = createUserWithRole(Role.MEMBER)
            ).id!!

            mvc.perform(delete("/events/signups/{eventSignupId}", signupId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class GuestAccessToken {

        @Test
        fun `allows guest access token holder to view own signup`() {
            val guest = createGuestFixture(accessToken = "guestToken123")
            createEventSignUpFixture(event = createEventFixture(), user = null, guest = guest)

            mvc.perform(
                get("/events/signups/byAccessToken/{accessToken}", guest.accessToken)
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest access token holder to update signup`() {
            val guest = createGuestFixture(accessToken = "guestToken123")
            val event = createEventFixture()
            val eventId = event.id!!
            val signUp = createEventSignUpFixture(event = event, user = null, guest = guest)

            mvc.perform(
                put("/events/{eventId}/signups", eventId)
                    .param("accessToken", guest.accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateSignUpGuestPayload(signUp.version, "Guest Updated"))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows guest access token holder to delete signup`() {
            val guest = createGuestFixture(accessToken = "guestToken123")
            val signupId = createEventSignUpFixture(event = createEventFixture(), user = null, guest = guest).id!!

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", guest.accessToken)
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies invalid guest access token from all operations`() {
            val signupId = createEventSignUpFixture(
                event = createEventFixture(),
                user = null,
                guest = createGuestFixture(accessToken = "validGuestToken")
            ).id!!
            val invalidToken = "invalidGuestToken"

            mvc.perform(
                delete("/events/signups/{eventSignupId}", signupId)
                    .param("accessToken", invalidToken)
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val eventId = createEventFixture().id!!

            mvc.perform(
                get("/events/{eventId}/signups", eventId)
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `BOARD can perform COMMITTEE operations`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = createEventFixture().id!!

            mvc.perform(
                post("/events/{eventId}/signups", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createSignUpGuestPayload())
            )
                .andExpect(status().isCreated)
        }
    }
}
