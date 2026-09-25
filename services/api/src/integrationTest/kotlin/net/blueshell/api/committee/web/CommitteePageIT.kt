package net.blueshell.api.committee.web

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** A committee's own page: its address, its members by Discord only, and what its members may change. */
@SpringBootTest
class CommitteePageIT : UserTestSupport() {
    @Autowired private lateinit var committeeRepository: CommitteeRepository

    private fun linked(
        user: User,
        tag: String,
    ): User {
        transactionTemplate.execute {
            userRepository.findById(user.id!!).orElseThrow().apply {
                discordId = "80351110224678912"
                discord = tag
            }
        }
        return refreshUser(user)
    }

    private fun addGame(board: User): String =
        JsonPath.read(
            mvc
                .perform(
                    post("/games")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Quiz${System.nanoTime()}","slug":"quiz-${System.nanoTime()}"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString,
            "$.code",
        )

    @Test
    fun `a committee's page answers to its address and names its members by Discord, never by name`() {
        val chair = linked(createUserWithRole(Role.MEMBER), "nelly")
        val unlinked = createUserWithRole(Role.MEMBER)
        val committee = createCommitteeFixture(name = "Page ${System.nanoTime()}")
        addCommitteeMember(committee, chair, role = "Chair")
        transactionTemplate.execute {
            val held = committeeRepository.findById(committee.id!!).orElseThrow()
            held.replaceMembers(held.members + committeeFactory.buildMember(held, unlinked, "Treasurer"))
        }

        val body =
            mvc
                .perform(get("/committees/address/{address}", committee.slug.uppercase()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value(committee.name))
                .andExpect(jsonPath("$.listed").value(true))
                .andReturn()
                .response.contentAsString

        val seats = JsonPath.read<List<Map<String, Any?>>>(body, "$.members")
        assertThat(seats).anySatisfy { assertThat(it).containsEntry("discordTag", "nelly").containsEntry("role", "Chair") }
        assertThat(seats).anySatisfy { assertThat(it["discordTag"]).isNull() }
        listOf(chair, unlinked).forEach { person ->
            assertThat(body).doesNotContain(person.firstName, person.lastName, person.email)
        }
        mvc.perform(get("/committees/address/{address}", "nobody-${System.nanoTime()}")).andExpect(status().isNotFound)
    }

    @Test
    fun `a committee's own members change its description and games, and nothing that is the board's`() {
        val member = createUserWithRole(Role.MEMBER)
        val outsider = createUserWithRole(Role.MEMBER)
        val committee = addCommitteeMember(createCommitteeFixture(name = "Own ${System.nanoTime()}"), member)
        val board = createUserWithRole(Role.BOARD)
        val game = addGame(board)
        val page = """{"description":"We run quizzes.","gameCodes":["$game"]}"""

        mvc
            .perform(put("/committees/{id}/page", committee.id).with(signedIn(member)).contentType(MediaType.APPLICATION_JSON).content(page))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("We run quizzes."))
            .andExpect(jsonPath("$.gameCodes", contains(game)))
        mvc
            .perform(put("/committees/{id}/page", committee.id).with(signedIn(outsider)).contentType(MediaType.APPLICATION_JSON).content(page))
            .andExpect(status().isForbidden)
        mvc
            .perform(
                put("/committees/{id}", committee.id)
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Renamed","description":"x","members":[{"userId":${member.id}}],"version":0}"""),
            ).andExpect(status().isForbidden)
        mvc
            .perform(get("/games/{game}/holdings", game).with(signedIn(board)))
            .andExpect(jsonPath("$.committees").value(1))
    }

    @Test
    fun `the board archives a committee, sets a game's organisers, and cannot give two committees one address`() {
        val board = createUserWithRole(Role.BOARD)
        val first = createCommitteeFixture(name = "First ${System.nanoTime()}")
        val second = addCommitteeMember(createCommitteeFixture(name = "Second ${System.nanoTime()}"), board)

        mvc
            .perform(
                put("/committees/{id}/archived", first.id).with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content("""{"archived":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.archived").value(true))
        mvc
            .perform(
                put("/committees/{id}", second.id)
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"name":"${second.name}","description":"x","members":[{"userId":${board.id}}],"version":${second.version},
                        "slug":"${first.slug}"}""",
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CommitteeAddressTaken"))
        mvc
            .perform(
                put("/committees/games/{game}", addGame(board))
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"committeeIds":[${first.id}]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", contains(first.id!!.toInt())))
    }
}
