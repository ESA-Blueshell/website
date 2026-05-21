package net.blueshell.api.domain.event.web

import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.factory.event.web.request.EventRequestFactory
import net.blueshell.api.factory.event.web.request.EventSignUpRequestFactory
import net.blueshell.api.shared.enums.QuestionType
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
import java.time.Instant

@SpringBootTest
class EventSignUpControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var eventSignUpRequestFactory: EventSignUpRequestFactory

    @Autowired
    private lateinit var eventRequestFactory: EventRequestFactory

    private fun attachSurvey(event: Event, vararg questions: Question): Event {
        val survey = Survey()
        val attached = questions.map { q ->
            Question(
                idx = q.idx,
                survey = survey,
                type = q.type,
                label = q.label,
                choiceLabels = q.choiceLabels?.toMutableList(),
                required = q.required,
            )
        }
        survey.replaceQuestions(attached)
        event.replaceSignUpForm(survey)
        return persist(event)
    }

    private fun openQuestion(idx: Long, label: String, required: Boolean): Question =
        Question(idx = idx, survey = Survey(), type = QuestionType.OPEN, label = label, required = required)

    private fun checkboxQuestion(idx: Long, label: String, required: Boolean, choices: MutableList<String>): Question =
        Question(idx = idx, survey = Survey(), type = QuestionType.CHECKBOX, label = label, choiceLabels = choices, required = required)

    @Nested
    inner class FindEventSignUps {
        @Test
        fun `board lists signups with filter`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, signUp = true)
            val signUp = createEventSignUpFixture(event = event, user = createUserWithRole(Role.MEMBER))

            mvc.perform(
                get("/events/signups")
                    .param("eventId", event.id!!.toString())
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(signUp.id))
        }

        @Test
        fun `signup filter keeps deleted user with anonymized identity`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = true, signUp = true)

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isCreated)

            mvc.perform(delete("/users/{userId}", member.id).with(bearer(board)))
                .andExpect(status().isNoContent)

            mvc.perform(
                get("/events/signups")
                    .param("eventId", event.id!!.toString())
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].user").isMap)
                .andExpect(jsonPath("$[0].user.fullName").value("Deleted User"))
                .andExpect(jsonPath("$[0].user.email").value(org.hamcrest.Matchers.startsWith("deleted-")))
        }
    }

    @Nested
    inner class FindEventSignUpsByAccessToken {
        @Test
        fun `guest finds signups by access token`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createGuestSignUpPayload())
            )
                .andExpect(status().isCreated)
                .andReturn()

            val created = mapper.readTree(createResult.response.contentAsByteArray)
            val signUpId = created.path("id").asLong()
            val accessToken = checkNotNull(
                createResult.response.getHeader(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER)
            ) { "Expected guest access token header on signup create response" }

            mvc.perform(
                get("/events/signups/byAccessToken")
                    .header(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER, accessToken)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(signUpId))
        }
    }

    @Nested
    inner class FindEventSignUpsByEventId {
        @Test
        fun `board lists signups for event`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, signUp = true)
            val signUp = createEventSignUpFixture(event = event, user = createUserWithRole(Role.MEMBER))

            mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(signUp.id))
        }

        @Test
        fun `event signup list keeps deleted user with anonymized identity`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = true, signUp = true)

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isCreated)

            mvc.perform(delete("/users/{userId}", member.id).with(bearer(board)))
                .andExpect(status().isNoContent)

            mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].user").isMap)
                .andExpect(jsonPath("$[0].user.fullName").value("Deleted User"))
                .andExpect(jsonPath("$[0].user.email").value(org.hamcrest.Matchers.startsWith("deleted-")))
        }
    }

    @Nested
    inner class CreateEventSignup {
        @Test
        fun `member creates signup`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.eventId").value(event.id))
                .andExpect(jsonPath("$.user.id").value(member.id))
        }

        @Nested
        inner class SignupLimits {
            @Test
            fun `rejects signup after deadline`() {
                val member = createUserWithRole(Role.MEMBER)
                val event = createEventFixture(
                    approved = true,
                    membersOnly = false,
                    signUp = true,
                    signUpDeadline = Instant.now().minusSeconds(1)
                )

                mvc.perform(
                    post("/events/{eventId}/signups", event.id)
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
                )
                    .andExpect(status().isBadRequest)
            }

            @Test
            fun `accepts signup before deadline`() {
                val member = createUserWithRole(Role.MEMBER)
                val event = createEventFixture(
                    approved = true,
                    membersOnly = false,
                    signUp = true,
                    signUpDeadline = Instant.now().plusSeconds(3600)
                )

                mvc.perform(
                    post("/events/{eventId}/signups", event.id)
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
                )
                    .andExpect(status().isCreated)
            }

            @Test
            fun `rejects signup at capacity`() {
                val member = createUserWithRole(Role.MEMBER)
                val anotherMember = createUserWithRole(Role.MEMBER)
                val event = createEventFixture(
                    approved = true,
                    membersOnly = false,
                    signUp = true,
                    signUpLimit = 1
                )
                createEventSignUpFixture(event = event, user = anotherMember)

                mvc.perform(
                    post("/events/{eventId}/signups", event.id)
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
                )
                    .andExpect(status().isBadRequest)
            }

            @Test
            fun `accepts signup when under limit`() {
                val member = createUserWithRole(Role.MEMBER)
                val anotherMember = createUserWithRole(Role.MEMBER)
                val event = createEventFixture(
                    approved = true,
                    membersOnly = false,
                    signUp = true,
                    signUpLimit = 2
                )
                createEventSignUpFixture(event = event, user = anotherMember)

                mvc.perform(
                    post("/events/{eventId}/signups", event.id)
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
                )
                    .andExpect(status().isCreated)
            }
        }

        @Test
        fun `anonymous signup cannot spoof a user id`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "userId": ${member.id},
                          "guest": {
                            "name": "Spoof Attempt",
                            "discord": "spoof#1234",
                            "email": "spoof@example.com",
                            "phoneNumber": "+31612345678"
                          }
                        }
                        """.trimIndent()
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.user").doesNotExist())
        }
    }

    @Nested
    inner class RequiredAndOptionalAnswers {
        @Test
        fun `accepts signup with blank optional open answer`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = attachSurvey(
                createEventFixture(approved = true, signUp = true),
                openQuestion(0, "Dietary notes?", required = false),
            )
            val questionId = event.signUpForm!!.questions.first().id!!

            val payload = eventSignUpRequestFactory.createUserSignUpPayload(
                member.id!!,
                eventSignUpRequestFactory.answersArray(
                    eventSignUpRequestFactory.openAnswerJson(questionId, ""),
                ),
            )

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `rejects signup with blank required open answer`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = attachSurvey(
                createEventFixture(approved = true, signUp = true),
                openQuestion(0, "Your name?", required = true),
            )
            val questionId = event.signUpForm!!.questions.first().id!!

            val payload = eventSignUpRequestFactory.createUserSignUpPayload(
                member.id!!,
                eventSignUpRequestFactory.answersArray(
                    eventSignUpRequestFactory.openAnswerJson(questionId, "   "),
                ),
            )

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `accepts checkbox signup with no selections when optional`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = attachSurvey(
                createEventFixture(approved = true, signUp = true),
                checkboxQuestion(0, "Allergies", required = false, choices = mutableListOf("Nuts", "Gluten", "Dairy")),
            )
            val questionId = event.signUpForm!!.questions.first().id!!

            val payload = eventSignUpRequestFactory.createUserSignUpPayload(
                member.id!!,
                eventSignUpRequestFactory.answersArray(
                    eventSignUpRequestFactory.selectionsAnswerJson(questionId, listOf(false, false, false)),
                ),
            )

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `rejects checkbox signup with no selections when required`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = attachSurvey(
                createEventFixture(approved = true, signUp = true),
                checkboxQuestion(0, "Pick", required = true, choices = mutableListOf("Pizza", "Pasta")),
            )
            val questionId = event.signUpForm!!.questions.first().id!!

            val payload = eventSignUpRequestFactory.createUserSignUpPayload(
                member.id!!,
                eventSignUpRequestFactory.answersArray(
                    eventSignUpRequestFactory.selectionsAnswerJson(questionId, listOf(false, false)),
                ),
            )

            mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload),
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class AmendSignupAfterFormChange {
        @Test
        fun `existing signup persists when board adds a new question and member can amend with new answer`() {
            val member = createUserWithRole(Role.MEMBER)
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()
            val event = attachSurvey(
                createEventFixture(committee = committee, approved = true, signUp = true),
                openQuestion(0, "Pre-existing question", required = false),
            )
            val originalQuestionId = event.signUpForm!!.questions.first().id!!

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventSignUpRequestFactory.createUserSignUpPayload(
                            member.id!!,
                            eventSignUpRequestFactory.answersArray(
                                eventSignUpRequestFactory.openAnswerJson(originalQuestionId, "first answer"),
                            ),
                        ),
                    ),
            )
                .andExpect(status().isCreated)
                .andReturn()

            val signUpId = mapper.readTree(createResult.response.contentAsByteArray).path("id").asLong()
            val signUpVersion = mapper.readTree(createResult.response.contentAsByteArray).path("version").asLong()

            mvc.perform(
                put("/events/{id}", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventRequestFactory.updateEventPayload(
                            committeeId = committee.id!!,
                            version = event.version,
                            approved = true,
                            startTime = Instant.now().plusSeconds(3600).toString(),
                            endTime = Instant.now().plusSeconds(7200).toString(),
                            signUpFormJson = eventRequestFactory.signUpFormJson(
                                eventRequestFactory.questionJson(0, "OPEN", "Pre-existing question"),
                                eventRequestFactory.questionJson(1, "OPEN", "New required question", required = true),
                            ),
                        ),
                    ),
            )
                .andExpect(status().isOk)

            mvc.perform(get("/events/{eventId}/signups", event.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(signUpId))
                .andExpect(jsonPath("$[0].answers[0].textResponse").value("first answer"))

            val refreshedEvent = mvc.perform(get("/events/{id}", event.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andReturn()
            val refreshedEventJson = mapper.readTree(refreshedEvent.response.contentAsByteArray)
            val newQuestionId = refreshedEventJson.path("signUpForm").path("questions")
                .first { it.path("idx").asLong() == 1L }
                .path("id").asLong()
            assertThat(newQuestionId).isPositive()

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        eventSignUpRequestFactory.updateUserSignUpPayload(
                            member.id!!,
                            signUpVersion,
                            eventSignUpRequestFactory.answersArray(
                                eventSignUpRequestFactory.openAnswerJson(originalQuestionId, "first answer"),
                                eventSignUpRequestFactory.openAnswerJson(newQuestionId, "answered later"),
                            ),
                        ),
                    ),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(signUpId))
                .andExpect(jsonPath("$.answers[?(@.questionId == $newQuestionId)].textResponse").value("answered later"))
        }
    }

    @Nested
    inner class UpdateEventSignUp {
        @Test
        fun `member updates own signup`() {
            val member = createUserWithRole(Role.MEMBER)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createUserSignUpPayload(member.id!!))
            )
                .andExpect(status().isCreated)
                .andReturn()

            val created = mapper.readTree(createResult.response.contentAsByteArray)
            val signUpId = created.path("id").asLong()
            val version = created.path("version").asLong()

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.updateUserSignUpPayload(member.id!!, version))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(signUpId))
        }

        @Test
        fun `guest updates signup using access token`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createGuestSignUpPayload())
            )
                .andExpect(status().isCreated)
                .andReturn()

            val created = mapper.readTree(createResult.response.contentAsByteArray)
            val signUpId = created.path("id").asLong()
            val accessToken = checkNotNull(
                createResult.response.getHeader(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER)
            ) { "Expected guest access token header on signup create response" }
            val version = created.path("version").asLong()

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .header(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER, accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.updateGuestSignUpPayload(version))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(signUpId))
        }
    }

    @Nested
    inner class DeleteEventSignup {
        @Test
        fun `board deletes signup by id`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createGuestSignUpPayload())
            )
                .andExpect(status().isCreated)
                .andReturn()

            val signUpId = mapper.readTree(createResult.response.contentAsByteArray).path("id").asLong()

            mvc.perform(delete("/events/signups/{id}", signUpId).with(bearer(board)))
                .andExpect(status().isNoContent)
        }

        @Test
        fun `guest deletes signup using access token`() {
            val board = createUserWithRole(Role.BOARD)
            val event = createEventFixture(approved = true, membersOnly = false, signUp = true)

            val createResult = mvc.perform(
                post("/events/{eventId}/signups", event.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(eventSignUpRequestFactory.createGuestSignUpPayload())
            )
                .andExpect(status().isCreated)
                .andReturn()

            val created = mapper.readTree(createResult.response.contentAsByteArray)
            val signUpId = created.path("id").asLong()
            val accessToken = checkNotNull(
                createResult.response.getHeader(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER)
            ) { "Expected guest access token header on signup create response" }

            mvc.perform(
                delete("/events/signups/{id}", signUpId)
                    .header(EventSignUpController.GUEST_ACCESS_TOKEN_HEADER, accessToken)
            )
                .andExpect(status().isNoContent)
        }
    }
}
