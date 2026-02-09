package net.blueshell.api.event.web

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.file.web.dto.FileDTO
import net.blueshell.api.event.web.dto.EventBannerDTO
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.factory.UnifiedFactory
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.user.persistence.User
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.EnumMap

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIT @Autowired constructor(
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

        val committee = committeeDTOFactory.createWithMemberRoles("Chair", "Member")
        committee.name = "VakanCie"
        committee.description = "Committee for events and drinks"
        committee.members[0].userId = board.id!!
        committee.members[1].userId = member.id!!

        val result = mvc.perform(
            post("/committees")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(committee))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
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

    private fun givenEventCreated(committeeId: Long): EventDTO {
        val board = users[Role.BOARD]!!

        val savedFile = givenUploadedBannerAsBoard()
        val banner = uf.with(EventBannerDTO::class.java) { b -> b.file = savedFile }

        val payload = uf.with(EventDTO::class.java) { e ->
            e.committeeId = committeeId
            e.title = "New Event"
            e.location = "Esports Lounge Twente"
            e.description = "The best description"
            e.approved = true
            e.membersOnly = true
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
                .content(mapper.writeValueAsBytes(payload))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.committeeId").value(committeeId.toInt()))
            .andExpect(jsonPath("$.title").value("New Event"))
            .andExpect(jsonPath("$.location").value("Esports Lounge Twente"))
            .andExpect(jsonPath("$.approved").value(true))
            .andExpect(jsonPath("$.membersOnly").value(true))
            .andExpect(jsonPath("$.signUp").value(true))
            .andExpect(jsonPath("$.signUpForm.questions", hasSize<Any>(4)))
            .andReturn()

        return mapper.readValue(result.response.contentAsByteArray, EventDTO::class.java)
    }

    @Test
    fun `events are created correctly`() {
        val committeeId = givenCommitteeId()
        val created = givenEventCreated(committeeId)
        assertNotNull(created.id)
        assertNotNull(created.signUpForm)
    }

    @Test
    fun `fetching events works`() {
        val committeeId = givenCommitteeId()
        givenEventCreated(committeeId)

        mvc.perform(get("/events").with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize<Any>(1)))
    }

    @Test
    fun `updates event`() {
        val committeeId = givenCommitteeId()
        val created = givenEventCreated(committeeId)

        mvc.perform(put("/events/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().is4xxClientError())

        val fresh = mapper.readValue(
            mvc.perform(get("/events/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
                .andExpect(status().isOk())
                .andReturn()
                .response
                .contentAsByteArray,
            EventDTO::class.java
        )

        fresh.title = "Updated Event"
        fresh.location = "Updated Location"
        fresh.description = "Updated Description"
        fresh.approved = false
        fresh.signUp = false

        mvc.perform(
            put("/events/{id}", fresh.id)
                .with(bearer(users[Role.BOARD]!!))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(fresh))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fresh.id!!.toInt()))
            .andExpect(jsonPath("$.committeeId").value(committeeId.toInt()))
            .andExpect(jsonPath("$.title").value("Updated Event"))
            .andExpect(jsonPath("$.location").value("Updated Location"))
            .andExpect(jsonPath("$.description").value("Updated Description"))
            .andExpect(jsonPath("$.approved").value(false))
            .andExpect(jsonPath("$.signUp").value(false))
    }

    @Test
    fun `deleting events removes them`() {
        val committeeId = givenCommitteeId()
        val created = givenEventCreated(committeeId)

        mvc.perform(get("/events").with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize<Any>(1)))

        mvc.perform(delete("/events/{id}", created.id).with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isNoContent())

        mvc.perform(get("/events").with(bearer(users[Role.BOARD]!!)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize<Any>(0)))
    }
}
