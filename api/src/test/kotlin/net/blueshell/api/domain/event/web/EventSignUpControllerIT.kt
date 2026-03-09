package net.blueshell.api.domain.event.web

import net.blueshell.api.factory.event.web.request.EventSignUpRequestFactory
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
import java.time.Instant

@SpringBootTest
class EventSignUpControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var eventSignUpRequestFactory: EventSignUpRequestFactory

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
