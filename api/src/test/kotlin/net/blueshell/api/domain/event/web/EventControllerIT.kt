package net.blueshell.api.domain.event.web

import net.blueshell.api.factory.event.web.request.EventRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class EventControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var eventRequestFactory: EventRequestFactory

    @Nested
    inner class CreateEvent {
        @Test
        fun `board creates approved event`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, approved = true))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.committeeId").value(committee.id))
                .andExpect(jsonPath("$.approved").value(true))
        }

        @Test
        fun `committee member creates event without approval power`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc.perform(
                post("/events")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, approved = true))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.approved").value(false))
        }

        @Test
        fun `create event is forbidden for outsider`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(outsider))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns bad request for invalid create payload`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"committeeId":${committee.id},"title":"","description":"Event description","location":"Campus","startTime":"2026-03-01T19:00:00Z","endTime":"2026-03-01T21:00:00Z","approved":true,"membersOnly":false,"signUp":true}"""
                    )
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `creates event with uploaded banner`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val bannerId = uploadBanner(board)

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, bannerFileId = bannerId))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.banner.fileId").value(bannerId))
        }
    }

    @Nested
    inner class UpdateEvent {
        @Test
        fun `committee member updates own committee event`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val event = createEventFixture(committee = committee, approved = false, title = "Before Update")

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            title = "After Update",
                            approved = true
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("After Update"))
                .andExpect(jsonPath("$.approved").value(false))
        }

        @Test
        fun `board updates event and can approve`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, approved = false, title = "Needs Approval")

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            title = "Board Updated",
                            approved = true
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("Board Updated"))
                .andExpect(jsonPath("$.approved").value(true))
        }

        @Test
        fun `update event is forbidden for outsider`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, approved = true)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(outsider))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.updateEventPayload(committeeId = committee.id!!, version = event.version))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns not found when event does not exist`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                put("/events/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.updateEventPayload(committeeId = committee.id!!, version = 0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class ApproveEvent {
        @Test
        fun `board approves event`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = false)

            mvc.perform(
                put("/events/{id}/approve", event.id)
                    .param("approved", "true")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(event.id))
                .andExpect(jsonPath("$.approved").value(true))
        }

        @Test
        fun `approve event is forbidden for committee member`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = false)

            mvc.perform(
                put("/events/{id}/approve", event.id)
                    .param("approved", "true")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class FindEventById {
        @Test
        fun `board reads unapproved event`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = false)

            mvc.perform(get("/events/{id}", event.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(event.id))
                .andExpect(jsonPath("$.approved").value(false))
        }

        @Test
        fun `committee member reads own unapproved event`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val event = createEventFixture(committee = committee, approved = false)

            mvc.perform(get("/events/{id}", event.id).with(bearer(member)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(event.id))
        }

        @Test
        fun `unapproved event is forbidden for outsider`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = false)

            mvc.perform(get("/events/{id}", event.id).with(bearer(outsider)))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `approved event is visible for authenticated guest`() {
            val guest = createUserWithRole(Role.GUEST)
            val event = createEventFixture(approved = true)

            mvc.perform(get("/events/{id}", event.id).with(bearer(guest)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(event.id))
                .andExpect(jsonPath("$.approved").value(true))
        }

        @Test
        fun `returns not found when event does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(get("/events/{id}", 999999L).with(bearer(board)))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindEvents {
        @Test
        fun `anonymous sees only approved events`() {
            createEventFixture(approved = true, title = "Public Event")
            createEventFixture(approved = false, title = "Private Draft")

            mvc.perform(get("/events").param("titleContains", "Event"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content[0].approved").value(true))
        }

        @Test
        fun `member sees unapproved events from own committee`() {
            val member = createUserWithRole(Role.MEMBER)
            val ownCommittee = addCommitteeMember(createCommitteeFixture(), member)
            val otherCommittee = createCommitteeFixture()
            val ownDraft = createEventFixture(committee = ownCommittee, approved = false, title = "Own Draft Event")
            createEventFixture(committee = otherCommittee, approved = false, title = "Other Draft Event")

            mvc.perform(
                get("/events")
                    .param("committeeId", ownCommittee.id!!.toString())
                    .param("approved", "false")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").value(ownDraft.id))
        }

        @Test
        fun `board can filter unapproved by title and committee`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val target = createEventFixture(committee = committee, approved = false, title = "Target Filter Event")
            createEventFixture(committee = committee, approved = false, title = "Noise Event")

            mvc.perform(
                get("/events")
                    .param("approved", "false")
                    .param("committeeId", committee.id!!.toString())
                    .param("titleContains", "Target")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").value(target.id))
                .andExpect(jsonPath("$.content[0].title").value("Target Filter Event"))
        }
    }

    @Nested
    inner class DeleteEventById {
        @Test
        fun `committee member deletes own committee event`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val event = createEventFixture(committee = committee)

            mvc.perform(
                delete("/events/{eventId}", event.id)
                    .with(bearer(member))
            )
                .andExpect(status().isNoContent)

            val board = createUserWithRole(Role.BOARD)
            mvc.perform(get("/events/{id}", event.id).with(bearer(board)))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `delete event is forbidden for outsider`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val event = createEventFixture()

            mvc.perform(
                delete("/events/{eventId}", event.id)
                    .with(bearer(outsider))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns not found when deleting missing event`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/events/{eventId}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }

    private fun uploadBanner(user: net.blueshell.api.domain.user.persistence.User): Long {
        val banner = eventRequestFactory.eventBannerMultipart()
        val uploadResult = mvc.perform(
            multipart("/events/banners")
                .file(banner)
                .with(bearer(user))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return mapper.readTree(uploadResult.response.contentAsByteArray).path("id").asLong()
    }
}
