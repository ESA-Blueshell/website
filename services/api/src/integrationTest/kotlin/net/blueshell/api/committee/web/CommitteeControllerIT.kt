package net.blueshell.api.committee.web

import net.blueshell.api.factory.committee.web.request.CommitteeRequestFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.DESCRIPTION_MAX
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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
            mvc
                .perform(get("/committeeMembers/committees"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isEmpty)
        }

        @Test
        fun `returns committees for current member`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc
                .perform(
                    get("/committeeMembers/committees")
                        .with(signedIn(member)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
        }
    }

    @Nested
    inner class FindCommittees {
        @Test
        fun `board receives detailed committees`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = addCommitteeMember(createCommitteeFixture(), board, role = "Chair")

            mvc
                .perform(
                    get("/committees")
                        .with(signedIn(board)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
                .andExpect(jsonPath("$[0].members").isArray)
        }

        @Test
        fun `board receives a seat that states no role`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = addCommitteeMember(createCommitteeFixture(), board, role = null)

            mvc
                .perform(
                    get("/committees")
                        .with(signedIn(board)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(committee.id))
                .andExpect(jsonPath("$[0].members[0].role").doesNotExist())
        }

        @Test
        fun `anonymous receives summary committees`() {
            val committee = createCommitteeFixture(name = "Summary Committee")

            mvc
                .perform(get("/committees"))
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

            mvc
                .perform(get("/committees/{committeeId}", committee.id).with(signedIn(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.members").isArray)
        }

        @Test
        fun `outsider receives committee summary by id`() {
            val outsider = createUserWithRole(Role.MEMBER)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc
                .perform(get("/committees/{committeeId}", committee.id).with(signedIn(outsider)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.members").doesNotExist())
        }

        @Test
        fun `returns not found when committee does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc
                .perform(get("/committees/{committeeId}", 999999L).with(signedIn(board)))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class CreateCommittee {
        @Test
        fun `creates committee`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            val result =
                mvc
                    .perform(
                        post("/committees")
                            .with(signedIn(board))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                committeeRequestFactory.createPayload(
                                    name = "Integration Committee",
                                    description = "Committee description",
                                    members =
                                        listOf(
                                            CommitteeRequestFactory.MemberInput(board.id!!, "Chair"),
                                            CommitteeRequestFactory.MemberInput(member.id!!, "Member"),
                                        ),
                                ),
                            ),
                    ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.id").isNumber)
                    .andExpect(jsonPath("$.members.length()").value(2))
                    .andReturn()

            val createdId = mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
            assertThat(createdId).isGreaterThan(0)
            assertThat(userRepository.findById(member.id!!).orElseThrow().roles).contains(Role.COMMITTEE)
        }

        @Test
        fun `keeps a description as long as Discord holds, emoji and mentions included`() {
            val board = createUserWithRole(Role.BOARD)
            // Four-byte emoji and a server emoji, so the column's character set is tested too.
            val said = "🍝 <:POGGERS:657733730491826186> <@123456789012345678> "
            val description = said + "a".repeat(DESCRIPTION_MAX - said.length)

            mvc
                .perform(
                    post("/committees")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            committeeRequestFactory.createPayload(
                                name = "Long Committee",
                                description = description,
                                members = listOf(CommitteeRequestFactory.MemberInput(board.id!!, "Chair")),
                            ),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.description").value(description))
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)

            mvc
                .perform(
                    post("/committees")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"","description":"","members":[]}"""),
                ).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateCommittee {
        @Test
        fun `updates committee and member list`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)

            mvc
                .perform(
                    put("/committees/{id}", committee.id)
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            committeeRequestFactory.updatePayload(
                                version = committee.version,
                                name = "Updated Committee",
                                description = "Updated description",
                                members =
                                    listOf(
                                        CommitteeRequestFactory.MemberInput(board.id!!, "Chair"),
                                    ),
                            ),
                        ),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(committee.id))
                .andExpect(jsonPath("$.name").value("Updated Committee"))
                .andExpect(jsonPath("$.members.length()").value(1))

            assertThat(userRepository.findById(member.id!!).orElseThrow().roles).doesNotContain(Role.COMMITTEE)
        }

        @Test
        fun `returns not found when committee does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc
                .perform(
                    put("/committees/{id}", 999999L)
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            committeeRequestFactory.updatePayload(
                                version = 0,
                                name = "Missing Committee",
                                description = "Missing",
                                members = listOf(CommitteeRequestFactory.MemberInput(board.id!!, "Chair")),
                            ),
                        ),
                ).andExpect(status().isNotFound)
        }

        @Test
        fun `refuses an edit made against a version somebody else has already saved over`() {
            val board = createUserWithRole(Role.BOARD)
            val committee = createCommitteeFixture(name = "Before")
            val seen = committee.version

            fun save(name: String) =
                mvc.perform(
                    put("/committees/{id}", committee.id)
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            committeeRequestFactory.updatePayload(
                                version = seen,
                                name = name,
                                description = "Edited",
                                members = listOf(CommitteeRequestFactory.MemberInput(board.id!!, "Chair")),
                            ),
                        ),
                )

            save("First edit").andExpect(status().isOk)
            save("Second edit").andExpect(status().isConflict)

            mvc
                .perform(get("/committees/{committeeId}", committee.id).with(signedIn(board)))
                .andExpect(jsonPath("$.name").value("First edit"))
        }
    }

    @Nested
    inner class DeleteCommitteeById {
        private fun committeeOf(eventId: Long): Long? =
            transactionTemplate.execute {
                val query = entityManager.createNativeQuery("select committee_id from events where id = ?").setParameter(1, eventId)
                (query.singleResult as Number?)?.toLong()
            }

        @Test
        fun `deletes a committee without events`() {
            val admin = createUserWithRole(Role.ADMIN)
            val committee = createCommitteeFixture()

            mvc
                .perform(get("/committees/{id}/deletion", committee.id).with(signedIn(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.events").value(0))
            mvc
                .perform(delete("/committees/{id}", committee.id).with(signedIn(admin)))
                .andExpect(status().isNoContent)

            mvc
                .perform(get("/committees/{committeeId}", committee.id).with(signedIn(admin)))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `hands a committee's events to the committee taking over, and refuses to leave them without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val committee = createCommitteeFixture()
            val taker = createCommitteeFixture(name = "Taker ${System.nanoTime()}")
            val event = createEventFixture(committee = committee)

            mvc
                .perform(get("/committees/{id}/deletion", committee.id).with(signedIn(admin)))
                .andExpect(jsonPath("$.events").value(1))
            mvc
                .perform(delete("/committees/{id}", committee.id).with(signedIn(admin)))
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("CommitteeEventsNeedTaker"))
                .andExpect(jsonPath("$.events").value(1))
            assertThat(committeeOf(event.id!!)).isEqualTo(committee.id)

            mvc
                .perform(delete("/committees/{id}", committee.id).param("takenOverBy", taker.id.toString()).with(signedIn(admin)))
                .andExpect(status().isNoContent)

            assertThat(committeeOf(event.id!!)).isEqualTo(taker.id)
        }

        @Test
        fun `returns not found when deleting missing committee`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc
                .perform(delete("/committees/{id}", 999999L).with(signedIn(admin)))
                .andExpect(status().isNotFound)
        }
    }
}
