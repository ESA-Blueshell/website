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
                .andExpect(jsonPath("$.guest.accessToken").isNotEmpty)
                .andReturn()

            val created = mapper.readTree(createResult.response.contentAsByteArray)
            val signUpId = created.path("id").asLong()
            val accessToken = created.path("guest").path("accessToken").asText()

            mvc.perform(get("/events/signups/byAccessToken/{accessToken}", accessToken))
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
            val accessToken = created.path("guest").path("accessToken").asText()
            val version = created.path("version").asLong()

            mvc.perform(
                put("/events/{eventId}/signups", event.id)
                    .param("accessToken", accessToken)
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
            val accessToken = created.path("guest").path("accessToken").asText()

            mvc.perform(
                delete("/events/signups/{id}", signUpId)
                    .param("accessToken", accessToken)
            )
                .andExpect(status().isNoContent)
        }
    }
}
