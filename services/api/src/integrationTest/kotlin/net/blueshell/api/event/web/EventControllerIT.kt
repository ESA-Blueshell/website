package net.blueshell.api.event.web

import net.blueshell.api.factory.event.web.request.EventRequestFactory
import net.blueshell.api.factory.event.web.request.EventSignUpRequestFactory
import java.time.Instant
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
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

    @Autowired
    private lateinit var eventSignUpRequestFactory: EventSignUpRequestFactory

    @Autowired
    private lateinit var fileRepository: FileRepository

    @Autowired
    private lateinit var eventSignUpRepository: EventSignUpRepository

    @Autowired
    private lateinit var eventRepository: EventRepository

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
    inner class SignUpLimitValidation {
        @Test
        fun `accepts event with signUpDeadline in the past`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.createEventPayload(
                            committee.id!!,
                            startTime = "2099-06-01T19:00:00Z",
                            endTime = "2099-06-01T21:00:00Z",
                            signUpDeadline = "2020-01-01T00:00:00Z"
                        )
                    )
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `rejects event with signUpDeadline after endTime`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.createEventPayload(
                            committee.id!!,
                            startTime = "2099-06-01T19:00:00Z",
                            endTime = "2099-06-01T21:00:00Z",
                            signUpDeadline = "2099-06-02T00:00:00Z"
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("signUpDeadline"))
        }

        @Test
        fun `error field name is signUpLimit when limit is too low`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, signUpLimit = 0))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[0].field").value("signUpLimit"))
        }

        @Test
        fun `accepts event with valid signUpDeadline`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.createEventPayload(
                            committee.id!!,
                            startTime = "2099-06-01T19:00:00Z",
                            endTime = "2099-06-01T21:00:00Z",
                            signUpDeadline = "2099-06-01T18:00:00Z"
                        )
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.signUpDeadline").value("2099-06-01T18:00:00Z"))
        }

        @Test
        fun `rejects event with signUpLimit of 0`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, signUpLimit = 0))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `accepts event with signUpLimit of 1`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, signUpLimit = 1))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.signUpLimit").value(1))
        }

        @Test
        fun `accepts event with no signUpDeadline and no signUpLimit`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.signUpDeadline").doesNotExist())
                .andExpect(jsonPath("$.signUpLimit").doesNotExist())
        }
    }

    @Nested
    inner class ValidationErrorFields {
        @Test
        fun `missing title returns error on field title`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val payload = """{"committeeId":${committee.id},"title":"","description":"desc","location":"here",
                |"startTime":"2099-06-01T19:00:00Z","endTime":"2099-06-01T21:00:00Z",
                |"approved":true,"membersOnly":false,"signUp":false}""".trimMargin()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
        }

        @Test
        fun `missing endTime returns error on field endTime`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val payload = """{"committeeId":${committee.id},"title":"T","description":"desc","location":"here",
                |"startTime":"2099-06-01T19:00:00Z",
                |"approved":true,"membersOnly":false,"signUp":false}""".trimMargin()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'endTime')]").exists())
        }

        @Test
        fun `signUpDeadline after endTime returns error on field signUpDeadline`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.createEventPayload(
                            committee.id!!,
                            startTime = "2099-06-01T19:00:00Z",
                            endTime = "2099-06-01T21:00:00Z",
                            signUpDeadline = "2099-06-02T00:00:00Z"
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'signUpDeadline')]").exists())
        }

        @Test
        fun `signUpLimit below 1 returns error on field signUpLimit`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, signUpLimit = 0))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'signUpLimit')]").exists())
        }

        @Test
        fun `signup deadline passed returns error on field eventId`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(
                approved = true,
                membersOnly = false,
                signUp = true,
                signUpDeadline = java.time.Instant.now().minusSeconds(1)
            )

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'eventId')]").exists())
        }

        @Test
        fun `signup at capacity returns error on field eventId`() {
            val member = createUserWithRole(Role.MEMBER)
            val other = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(
                approved = true,
                membersOnly = false,
                signUp = true,
                signUpLimit = 1
            )
            createEventSignUpFixture(event = event, user = other)

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[?(@.field == 'eventId')]").exists())
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

        @Test
        fun `replacing banner deletes old file when no other banner uses it`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val oldFile = createFileFixture(uploader = board, name = "banner-old.png")
            val newFile = createFileFixture(uploader = board, name = "banner-new.png")
            val event = attachEventBanner(createEventFixture(committee = committee), oldFile)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            bannerFileId = newFile.id
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner.fileId").value(newFile.id))

            assertThat(fileRepository.existsById(newFile.id!!)).isTrue()
            assertThat(fileRepository.existsById(oldFile.id!!)).isFalse()
        }

        @Test
        fun `replacing banner keeps old file when another banner still uses it`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val sharedFile = createFileFixture(uploader = board, name = "banner-shared.png")
            val replacementFile = createFileFixture(uploader = board, name = "banner-replacement.png")
            val eventA = attachEventBanner(createEventFixture(committee = committee), sharedFile)
            attachEventBanner(createEventFixture(committee = committee), sharedFile)

            mvc.perform(
                put("/events/{id}", eventA.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = eventA.version,
                            bannerFileId = replacementFile.id
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner.fileId").value(replacementFile.id))

            assertThat(fileRepository.existsById(sharedFile.id!!)).isTrue()
            assertThat(fileRepository.existsById(replacementFile.id!!)).isTrue()
        }

        @Test
        fun `adding a sign up form keeps existing signups by default`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, signUp = true)
            val member = createUserWithRole(Role.MEMBER)
            createEventSignUpFixture(event = event, user = member)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "DESCRIPTION", "Read this first")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)
        }

        @Test
        fun `adding a non description question keeps existing signups by default`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, signUp = true)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "DESCRIPTION", "Read this first")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            val eventWithForm = eventRepository.findById(event.id!!).orElseThrow()
            createEventSignUpFixture(event = eventWithForm, user = createUserWithRole(Role.MEMBER))
            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = eventWithForm.version,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "DESCRIPTION", "Read this first"),
                                questionJson(1, "OPEN", "Any allergies?")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)
        }

        @Test
        fun `removeExistingSignUps flag deletes existing signups on update`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, signUp = true)
            val member = createUserWithRole(Role.MEMBER)
            createEventSignUpFixture(event = event, user = member)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            removeExistingSignUps = true,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "OPEN", "Any allergies?")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).isEmpty()
        }

        @Test
        fun `adding only description questions keeps existing signups`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = createEventFixture(committee = committee, signUp = true)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "DESCRIPTION", "Base info")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            val eventWithForm = eventRepository.findById(event.id!!).orElseThrow()
            createEventSignUpFixture(event = eventWithForm, user = createUserWithRole(Role.MEMBER))
            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = eventWithForm.version,
                            signUpFormJson = signUpFormJson(
                                questionJson(0, "DESCRIPTION", "Base info"),
                                questionJson(1, "DESCRIPTION", "Extra note")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)

            assertThat(eventSignUpRepository.findByEvent_Id(event.id!!)).hasSize(1)
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

    @Nested
    inner class PublicBannerArt {

        /**
         * The art an event carries is drawn by a page anybody can visit, so it answers with a
         * url rather than only the id of a file behind a permission check.
         */
        @Test
        fun `an event answers with promo art a visitor can fetch without logging in`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val bannerId = uploadBanner(board)

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, bannerFileId = bannerId))
            ).andExpect(status().isCreated)

            val listed = mvc.perform(get("/events"))
                .andExpect(status().isOk)
                .andReturn().response.contentAsByteArray
            val art = mapper.readTree(listed).path("content").first().path("banner").path("image")

            assertThat(art.path("url").asText()).isNotBlank()
            assertThat(art.path("path").asText()).isNotBlank()

            // And the url answers to somebody who is not logged in, which is the point of it.
            mvc.perform(get(art.path("url").asText()))
                .andExpect(status().isOk)
        }

        /** Art uploaded now is stored at the widths a page asks for, so a phone fetches one. */
        @Test
        fun `promo art is stored at several widths`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val bannerId = uploadBanner(board)

            mvc.perform(
                post("/events")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventRequestFactory.createEventPayload(committee.id!!, bannerFileId = bannerId))
            ).andExpect(status().isCreated)

            val listed = mvc.perform(get("/events"))
                .andExpect(status().isOk)
                .andReturn().response.contentAsByteArray
            val renditions = mapper.readTree(listed).path("content").first()
                .path("banner").path("image").path("renditions")

            assertThat(renditions.isArray).isTrue()
            assertThat(renditions.size()).isGreaterThan(0)
            renditions.forEach { copy ->
                assertThat(copy.path("width").asInt()).isGreaterThan(0)
                assertThat(copy.path("url").asText()).isNotBlank()
            }
        }

        /** The file id and version stay: the editor names exactly those to replace the art. */
        @Test
        fun `the art answers beside the file the editor names`() {
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
                .andExpect(jsonPath("$.banner.image.url").exists())
        }

        /** An event carrying nothing says so, rather than answering with an empty image. */
        @Test
        fun `an event with no art carries no banner`() {
            createEventFixture()

            mvc.perform(get("/events"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].banner").doesNotExist())
        }
    }

    private fun uploadBanner(user: net.blueshell.api.user.persistence.User): Long {
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

    private fun questionJson(idx: Long, type: String, label: String): String {
        return eventRequestFactory.questionJson(idx = idx, type = type, label = label)
    }

    private fun signUpFormJson(vararg questions: String): String {
        return eventRequestFactory.signUpFormJson(*questions)
    }
}
