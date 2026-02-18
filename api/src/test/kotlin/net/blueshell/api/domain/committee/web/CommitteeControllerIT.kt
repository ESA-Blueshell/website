package net.blueshell.api.domain.committee.web

import net.blueshell.api.factory.committee.web.request.CommitteeRequestFactory
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
class CommitteeControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var committeeRequestFactory: CommitteeRequestFactory

    @Nested
    inner class FindCommitteesByUserId {
        @Test
        fun `returns empty list when unauthenticated`() {
            mvc.perform(get("/committeeMembers/committees"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isEmpty)
        }

        @Test
        fun `returns committees for current member`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc.perform(
                get("/committeeMembers/committees")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
        }
    }

    @Nested
    inner class FindCommittees {
        @Test
        fun `board receives detailed committees`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = addCommitteeMember(createCommitteeFixture(), board, role = "Chair")

            mvc.perform(
                get("/committees")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
                .andExpect(jsonPath("$[0].members").isArray)
        }

        @Test
        fun `anonymous receives summary committees`() {
            val committee = createCommitteeFixture(name = "Summary Committee")

            mvc.perform(get("/committees"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
                .andExpect(jsonPath("$[0].name").value("Summary Committee"))
                .andExpect(jsonPath("$[0].members").doesNotExist())
        }
    }

    @Nested
    inner class FindCommitteeById {
        @Test
        fun `board finds committee detail by id`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc.perform(get("/committees/{committeeId}", committee.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.members").isArray)
        }

        @Test
        fun `outsider receives committee summary by id`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc.perform(get("/committees/{committeeId}", committee.id).with(bearer(outsider)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.members").doesNotExist())
        }

        @Test
        fun `returns not found when committee does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(get("/committees/{committeeId}", 999999L).with(bearer(board)))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class CreateCommittee {
        @Test
        fun `creates committee`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            val result = mvc.perform(
                post("/committees")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        committeeRequestFactory.createPayload(
                            name = "Integration Committee",
                            description = "Committee description",
                            members = listOf(
                                CommitteeRequestFactory.MemberInput(board.id!!, "Chair"),
                                CommitteeRequestFactory.MemberInput(member.id!!, "Member")
                            )
                        )
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.members.length()").value(2))
                .andReturn()

            val createdId = mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
            assertThat(createdId).isGreaterThan(0)
            assertThat(userRepository.findById(member.id!!).orElseThrow().roles).contains(Role.COMMITTEE)
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/committees")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"","description":"","members":[]}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateCommittee {
        @Test
        fun `updates committee and member list`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc.perform(
                put("/committees/{id}", committee.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        committeeRequestFactory.updatePayload(
                            version = committee.version,
                            name = "Updated Committee",
                            description = "Updated description",
                            members = listOf(
                                CommitteeRequestFactory.MemberInput(board.id!!, "Chair")
                            )
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.name").value("Updated Committee"))
                .andExpect(jsonPath("$.members.length()").value(1))

            assertThat(userRepository.findById(member.id!!).orElseThrow().roles).doesNotContain(Role.COMMITTEE)
        }

        @Test
        fun `returns not found when committee does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                put("/committees/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        committeeRequestFactory.updatePayload(
                            version = 0,
                            name = "Missing Committee",
                            description = "Missing",
                            members = listOf(CommitteeRequestFactory.MemberInput(board.id!!, "Chair"))
                        )
                    )
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteCommitteeById {
        @Test
        fun `deletes committee`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture()

            mvc.perform(delete("/committees/{id}", committee.id).with(bearer(board)))
                .andExpect(status().isNoContent)

            mvc.perform(get("/committees/{committeeId}", committee.id).with(bearer(board)))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `returns not found when deleting missing committee`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(delete("/committees/{id}", 999999L).with(bearer(board)))
                .andExpect(status().isNotFound)
        }
    }
}
