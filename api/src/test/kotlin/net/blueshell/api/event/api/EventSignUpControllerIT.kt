package net.blueshell.api.event.api

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.file.api.dto.FileDTO
import net.blueshell.api.event.api.dto.GuestDTO
import net.blueshell.api.event.api.dto.EventBannerDTO
import net.blueshell.api.event.api.dto.EventDTO
import net.blueshell.api.event.api.dto.EventSignUpDTO
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.user.api.dto.SimpleUserDTO
import net.blueshell.api.factory.UnifiedFactory
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.user.domain.model.User
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.math.min

@SpringBootTest
@AutoConfigureMockMvc
class EventSignUpControllerIT @Autowired constructor(
    private val uf: UnifiedFactory,
    private val committeeDTOFactory: AdvancedCommitteeDTOFactory,
    private val surveyFactory: SurveyDTOFactory
) : UserTestSupport() {

    private val users = EnumMap<Role, User>(Role::class.java)

    @BeforeEach
    fun setupUsers() {
        users[Role.MEMBER] = createUserWithRole(Role.MEMBER)
        users[Role.BOARD] = createUserWithRole(Role.BOARD)
    }

    private fun givenCommitteeId(): Long {
        val board = users[Role.BOARD]!!
        val member = users[Role.MEMBER]!!

        val advancedCommitteeDTO = committeeDTOFactory.createWithMemberRoles("Chair", "Member")
        advancedCommitteeDTO.name = "SignUp Committee"
        advancedCommitteeDTO.description = "Committee for signup testing"
        advancedCommitteeDTO.members[0].userId = board.id
        advancedCommitteeDTO.members[1].userId = member.id

        val result = mvc.perform(
            post("/committees")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(advancedCommitteeDTO))
        )
            .andExpect(status().isCreated())
            .andReturn()

        return mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
    }

    private fun givenUploadedBannerAsBoard(): FileDTO {
        val board = users[Role.BOARD]!!
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val banner = MockMultipartFile("file", "banner.jpg", "image/jpeg", imageBytes)

        val result = mvc.perform(multipart("/events/banners").file(banner).with(bearer(board)))
            .andExpect(status().isCreated())
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, FileDTO::class.java)
    }

    private fun givenEventWithForm(committeeId: Long): EventDTO {
        val board = users[Role.BOARD]!!

        val savedFile = givenUploadedBannerAsBoard()
        val banner = uf.with(EventBannerDTO::class.java) { b -> b.file = savedFile }

        val eventDTO = uf.with(EventDTO::class.java) { e ->
            e.committeeId = committeeId
            e.title = "Signup Test Event"
            e.approved = true
            e.membersOnly = false
            e.signUp = true
            e.banner = banner
            e.signUpForm = surveyFactory.createWithQuestionTypes(
                QuestionType.DESCRIPTION,
                QuestionType.RADIO,
                QuestionType.CHECKBOX,
                QuestionType.OPEN
            )
        }

        val result = mvc.perform(
            post("/events")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(eventDTO))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.signUp").value(true))
            .andExpect(jsonPath("$.signUpForm.questions", hasSize<Any>(4)))
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, EventDTO::class.java)
    }

    private fun answersFor(event: EventDTO, variant: String): MutableList<AnswerDTO> {
        val questions = event.signUpForm?.questions ?: return mutableListOf()

        val answers = mutableListOf<AnswerDTO>()
        for (question in questions) {
            if (question.type == QuestionType.DESCRIPTION) continue

            answers.add(
                uf.with(AnswerDTO::class.java) { answer ->
                    answer.questionId = question.id
                    when (question.type) {
                        QuestionType.OPEN -> {
                            answer.textResponse = if (variant == "create") "Initial response" else "Updated response"
                        }

                        QuestionType.CHECKBOX -> {
                            val n = question.choiceLabels?.size ?: 0
                            answer.optionSelections = MutableList(n) { idx ->
                                (variant == "create") == (idx % 2 == 0)
                            }
                        }

                        QuestionType.RADIO -> {
                            val n = question.choiceLabels?.size ?: 0
                            val picks = MutableList(n) { false }
                            if (n > 0) {
                                val pickIndex = if (variant == "create") 0 else min(1, n - 1)
                                picks[pickIndex] = true
                            }
                            answer.optionSelections = picks
                        }

                        else -> {}
                    }
                }
            )
        }

        return answers
    }

    @Test
    fun `create list update delete member flow and filter endpoints`() {
        val committeeId = givenCommitteeId()
        val event = givenEventWithForm(committeeId)

        val member = users[Role.MEMBER]!!
        var eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
            es.eventId = event.id
            es.userId = member.id
            es.user = uf.with(SimpleUserDTO::class.java) { u ->
                u.id = member.id
                u.version = member.version
            }
            es.answers = answersFor(event, "create")
        }

        val createdRes = mvc.perform(
            post("/events/{eventId}/signups", event.id)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(eventSignUpDTO))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.answers", not(empty<Any>())))
            .andReturn()

        eventSignUpDTO = mapper.readValue(createdRes.response.contentAsByteArray, EventSignUpDTO::class.java)
        val createdId = eventSignUpDTO.id!!

        mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].eventId", everyItem(`is`(event.id!!.toInt()))))
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        mvc.perform(
            get("/events/signups")
                .queryParam("userId", member.id.toString())
                .with(bearer(users[Role.BOARD]!!))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user.id").value(member.id!!.toInt()))
            .andExpect(jsonPath("$[0].eventId").value(event.id!!.toInt()))

        eventSignUpDTO.answers = answersFor(event, "update")
        mvc.perform(
            put("/events/{eventId}/signups", event.id)
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(eventSignUpDTO))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(createdId.toInt()))

        mvc.perform(delete("/events/signups/{id}", createdId).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isNoContent())

        mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }

    @Test
    fun `guest flow create then list by access token and update with token`() {
        val committeeId = givenCommitteeId()
        val event = givenEventWithForm(committeeId)
        val board = users[Role.BOARD]!!

        var eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
            es.eventId = event.id
            es.answers = answersFor(event, "create")
            es.guest = uf.with(GuestDTO::class.java) { g ->
                g.email = "guest.name@example.com"
                g.name = "Guesty McGuestface"
                g.discord = "Discord"
                g.phoneNumber = "0611111111"
            }
            es.user = null
            es.userId = null
        }

        val createdRes = mvc.perform(
            post("/events/{eventId}/signups", event.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(eventSignUpDTO))
        )
            .andExpect(status().isCreated())
            .andReturn()

        eventSignUpDTO = mapper.readValue(createdRes.response.contentAsByteArray, EventSignUpDTO::class.java)
        val token = requireNotNull(eventSignUpDTO.guest!!.accessToken)
        assertNotNull(token)

        mvc.perform(get("/events/signups/byAccessToken/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(eventSignUpDTO.id))
            .andExpect(jsonPath("$[0].eventId").value(event.id!!.toInt()))

        eventSignUpDTO.answers = answersFor(event, "update")
        eventSignUpDTO.guest!!.name = "Guesty Updated"
        eventSignUpDTO.guest!!.accessToken = token

        mvc.perform(
            put("/events/{eventId}/signups", event.id)
                .queryParam("accessToken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(eventSignUpDTO))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(event.id!!.toInt()))

        mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Nested
    inner class CommitteeAndEvent {
        @Test
        fun `board can create committee and event with form`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            assertNotNull(event.id)
            assertNotNull(event.signUpForm)
            assertFalse(event.signUpForm!!.questions.isEmpty())
        }
    }

    @Nested
    inner class MemberSignup {
        @Test
        fun `member can create signup`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val member = users[Role.MEMBER]!!

            val eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.user = uf.with(SimpleUserDTO::class.java) { u ->
                    u.id = member.id
                    u.version = member.version
                }
                es.answers = answersFor(event, "create")
            }

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(event.id!!.toInt()))
        }

        @Test
        fun `member can update own signup`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val member = users[Role.MEMBER]!!

            var eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.user = uf.with(SimpleUserDTO::class.java) { u ->
                    u.id = member.id
                    u.version = member.version
                }
                es.answers = answersFor(event, "create")
            }

            val createdRes = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isCreated())
                .andReturn()

            eventSignUpDTO = mapper.readValue(createdRes.response.contentAsByteArray, EventSignUpDTO::class.java)
            eventSignUpDTO.answers = answersFor(event, "update")

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventSignUpDTO.id))
        }

        @Test
        fun `board can filter signups by user and event`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val member = users[Role.MEMBER]!!
            val board = users[Role.BOARD]!!

            val eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.user = uf.with(SimpleUserDTO::class.java) { u ->
                    u.id = member.id
                    u.version = member.version
                }
                es.answers = answersFor(event, "create")
            }

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isCreated())

            mvc.perform(
                get("/events/signups")
                    .queryParam("userId", member.id.toString())
                    .with(bearer(board))
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.id").value(member.id!!.toInt()))
                .andExpect(jsonPath("$[0].eventId").value(event.id!!.toInt()))
        }
    }

    @Nested
    inner class GuestSignupByAccessToken {
        @Test
        fun `guest can create signup and fetch by token`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val board = users[Role.BOARD]!!

            var eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.answers = answersFor(event, "create")
                es.guest = uf.with(GuestDTO::class.java) { g ->
                    g.email = "guest.name@example.com"
                    g.name = "Guesty McGuestface"
                    g.discord = "Discord"
                    g.phoneNumber = "0611111111"
                }
                es.user = null
                es.userId = null
            }

            val createdRes = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isCreated())
                .andReturn()

            eventSignUpDTO = mapper.readValue(createdRes.response.contentAsByteArray, EventSignUpDTO::class.java)
            val token = requireNotNull(eventSignUpDTO.guest!!.accessToken)
            assertNotNull(token)

            mvc.perform(get("/events/signups/byAccessToken/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize<Any>(1)))
        }

        @Test
        fun `guest can update signup using access token`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val board = users[Role.BOARD]!!

            var eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.answers = answersFor(event, "create")
                es.guest = uf.with(GuestDTO::class.java) { g ->
                    g.email = "guest.name@example.com"
                    g.name = "Guesty"
                    g.discord = "Discord"
                    g.phoneNumber = "0611111111"
                }
                es.user = null
                es.userId = null
            }

            val createdRes = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isCreated())
                .andReturn()

            eventSignUpDTO = mapper.readValue(createdRes.response.contentAsByteArray, EventSignUpDTO::class.java)
            val token = requireNotNull(eventSignUpDTO.guest!!.accessToken)

            eventSignUpDTO.answers = answersFor(event, "update")
            eventSignUpDTO.guest!!.name = "Guesty Updated"

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .queryParam("accessToken", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.id!!.toInt()))
        }

        @Test
        fun `update by token fails for missing or invalid token`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.answers = answersFor(event, "update")
                es.guest = uf.with(GuestDTO::class.java) { g ->
                    g.email = "guest.name@example.com"
                    g.name = "Guesty McGuestface"
                    g.discord = "Discord"
                    g.phoneNumber = "0611111111"
                }
            }

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .queryParam("accessToken", "NOT_A_REAL_TOKEN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isNotFound())
        }
    }

    @Nested
    inner class Validation {
        @Test
        fun `body requires guest or user when no access token`() {
            val committeeId = givenCommitteeId()
            val event = givenEventWithForm(committeeId)
            val board = users[Role.BOARD]!!

            val eventSignUpDTO = uf.with(EventSignUpDTO::class.java) { es ->
                es.eventId = event.id
                es.answers = answersFor(event, "update")
                es.guest = null
                es.user = null
                es.userId = null
            }

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(eventSignUpDTO))
            )
                .andExpect(status().isBadRequest())
        }
    }
}
