package net.blueshell.api.event.web

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
    private fun createEventPayload(
        committeeId: Long,
        title: String = "Board Event",
        approved: Boolean = true,
        membersOnly: Boolean = false,
        signUp: Boolean = true
    ): String =
        """{"committeeId":$committeeId,"title":"$title","description":"Event description","location":"Campus","startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z","approved":$approved,"membersOnly":$membersOnly,"signUp":$signUp}"""

    private fun updateEventPayload(
        committeeId: Long,
        version: Long,
        title: String = "Updated Event",
        approved: Boolean = true,
        membersOnly: Boolean = false,
        signUp: Boolean = true,
        bannerFileId: Long? = null
    ): String {
        val bannerPart = if (bannerFileId == null) "" else ""","banner":{"fileId":$bannerFileId}"""
        return """{"committeeId":$committeeId,"title":"$title","description":"Updated event description","location":"Campus","startTime":"2026-02-14T19:00:00Z","endTime":"2026-02-14T21:00:00Z","approved":$approved,"membersOnly":$membersOnly,"signUp":$signUp,"version":$version$bannerPart}"""
    }

    @Nested
    inner class CreateEvent {

        @Test
        fun `allows BOARD to create events`() {
            val board = createUserWithRole(Role.BOARD)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committeeId))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows committee member to create event for their committee`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)

            mvc.perform(
                post("/events")
                    .with(bearer(committeeUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committee.id!!, "Committee Event"))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies regular user from creating events`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                post("/events")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committeeId, "Unauthorized Event"))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies GUEST from creating events`() {
            val guest = createUserWithRole(Role.GUEST)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                post("/events")
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committeeId, "Guest Event"))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val committeeId = createCommitteeFixture().id!!
            mvc.perform(
                post("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committeeId, "Unauthorized Event"))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateEvent {

        @Test
        fun `allows BOARD to update any event`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture()
            val eventId = event.id!!
            val targetCommittee = createCommitteeFixture()

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(targetCommittee.id!!, event.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows committee member to update their event`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)
            val event = createEventFixture(committee = committee)
            val eventId = event.id!!

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(committeeUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(committee.id!!, event.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies committee member from reassigning event to committee they do not belong to`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val ownCommittee = createCommitteeFixture(name = "Own Committee ${System.currentTimeMillis()}")
            val otherCommittee = createCommitteeFixture(name = "Other Committee ${System.currentTimeMillis()}")
            addCommitteeMember(ownCommittee, committeeUser)
            val event = createEventFixture(committee = ownCommittee)
            val eventId = event.id!!

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(committeeUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(otherCommittee.id!!, event.version, "Cross Committee Move"))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from updating events`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture()
            val eventId = event.id!!

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(event.committee!!.id!!, event.version, "Hacked Event"))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val event = createEventFixture()
            val eventId = event.id!!

            mvc.perform(
                put("/events/{id}", eventId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(event.committee!!.id!!, event.version, "Unauthorized Update"))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `allows updating an event that already has a banner without constraint violation`() {
            val board = createUserWithRole(Role.BOARD)
            val file = createFileFixture(uploader = board)
            val event = attachEventBanner(createEventFixture(), file)
            val eventId = event.id!!

            mvc.perform(
                put("/events/{id}", eventId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateEventPayload(event.committee!!.id!!, event.version, bannerFileId = file.id!!))
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class ApproveEvent {

        @Test
        fun `allows BOARD to approve events`() {
            val board = createUserWithRole(Role.BOARD)
            val eventId = createEventFixture(approved = false).id!!

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
            val eventId = createEventFixture(approved = true).id!!

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
            val eventId = createEventFixture().id!!

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
            val eventId = createEventFixture().id!!

            mvc.perform(
                put("/events/{id}/approve", eventId)
                    .param("approved", "true")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = createEventFixture().id!!

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
            val eventId = createEventFixture(approved = false).id!!

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows committee member to read their event`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)
            val eventId = createEventFixture(committee = committee, approved = false).id!!

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(committeeUser))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows regular user to read approved event`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture(approved = true).id!!

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies regular user from reading unapproved event`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture(approved = false).id!!

            mvc.perform(
                get("/events/{id}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated for restricted event`() {
            val eventId = createEventFixture(approved = false).id!!

            mvc.perform(
                get("/events/{id}", eventId)
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `allows unauthenticated to read approved event`() {
            val eventId = createEventFixture(approved = true).id!!

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
            val eventId = createEventFixture().id!!

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies committee member from deleting events`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val eventId = createEventFixture().id!!

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies regular user from deleting events`() {
            val member = createUserWithRole(Role.MEMBER)
            val eventId = createEventFixture().id!!

            mvc.perform(
                delete("/events/{eventId}", eventId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val eventId = createEventFixture().id!!

            mvc.perform(delete("/events/{eventId}", eventId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val eventId = createEventFixture().id!!

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
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createEventPayload(committeeId, "Board Event"))
            )
                .andExpect(status().isCreated)
        }
    }
}
