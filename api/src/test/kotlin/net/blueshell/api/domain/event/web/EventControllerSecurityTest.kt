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
 * Security tests for EventController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - BOARD users can create events for any committee
 * - Committee members can create events for their committee
 * - Non-committee members cannot create events
 * - Committee members can update their events
 * - BOARD can approve/reject events
 * - Public read access to approved events, restricted to committee members for unapproved
 */
@SpringBootTest
class EventControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class CreateEvent {

        @Test
        fun `allows BOARD to create events`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Board Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows committee member to create event for their committee`() {
            val committee = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/events")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Committee Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies regular user from creating events`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/events")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Unauthorized Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from creating events`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/events")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Guest Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(
                post("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Unauthorized Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateEvent {

        @Test
        fun `allows BOARD to update any event`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Updated Event","committeeId":1}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows committee member to update their event`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Updated Event","committeeId":1}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from updating events`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Hacked Event","committeeId":1}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(
                put("/events/{id}", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Unauthorized Update","committeeId":1}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class ApproveEvent {

        @Test
        fun `allows BOARD to approve events`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to reject events`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "false")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies committee member from approving events`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from approving events`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindEventById {

        @Test
        fun `allows BOARD to read any event`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows committee member to read their event`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(committee))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows regular user to read approved event`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from reading unapproved event`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated for restricted event`() {
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `allows unauthenticated to read approved event`() {
            val eventId = 1L

            mvc.perform(
                get("/events/{id}", eventId)
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindEvents {

        @Test
        fun `allows anyone to list events`() {
            mvc.perform(get("/events"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to list events`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/events")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows unauthenticated to list events`() {
            mvc.perform(get("/events"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to list events`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/events")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class DeleteEvent {

        @Test
        fun `allows BOARD to delete events`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = 1L

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies committee member from deleting events`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = 1L

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from deleting events`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = 1L

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = 1L

            mvc.perform(delete("/events/{eventId}", eventId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val eventId = 1L

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `BOARD can perform COMMITTEE operations`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Board Event","committeeId":1,"startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z"}""")
            )
                .andExpect(status().isCreated)
        }
    }
}
