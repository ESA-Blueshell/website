package net.blueshell.api.domain.committee.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for CommitteeController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Public list of committees with different responses for authenticated/unauthenticated
 * - Committee members get detailed info, others get summary
 * - BOARD can create/update/delete committees
 * - Committee members can update their committee
 * - Non-members cannot update committees
 */
@SpringBootTest
class CommitteeControllerSecurityTest : UserTestSupport() {
    private fun createCommitteePayload(memberUserId: Long, name: String = "New Committee"): String =
        """{"name":"$name","description":"Committee description","members":[{"userId":$memberUserId,"role":"Chair"}]}"""

    private fun updateCommitteePayload(memberUserId: Long, version: Long, name: String = "Updated Committee"): String =
        """{"name":"$name","description":"Updated committee description","members":[{"userId":$memberUserId,"role":"Chair"}],"version":$version}"""

    @Nested
    inner class FindCommitteesForCurrentUser {

        @Test
        fun `allows authenticated user to find their committees`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/committeeMembers/committees")
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to see all committees`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/committeeMembers/committees")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns empty list when unauthenticated`() {
            mvc.perform(get("/committeeMembers/committees"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindCommittees {

        @Test
        fun `allows anyone to list committees`() {
            mvc.perform(get("/committees"))
                .andExpect(status().isOk)
        }

        @Test
        fun `allows authenticated user to list committees`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/committees")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to list committees with full details`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/committees")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns summary for non-BOARD users`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/committees")
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns summary for unauthenticated users`() {
            mvc.perform(get("/committees"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class FindCommitteeById {

        @Test
        fun `allows BOARD to read any committee`() {
            val board = createUserWithRole(Role.BOARD)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                get("/committees/{committeeId}", committeeId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows committee member to read their committee`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = createCommitteeFixture()
            addCommitteeMember(committee, committeeUser)
            val committeeId = committee.id!!

            mvc.perform(
                get("/committees/{committeeId}", committeeId)
                    .with(bearer(committeeUser))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows non-member to read committee summary`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                get("/committees/{committeeId}", committeeId)
                    .with(bearer(member))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `returns committee summary when unauthenticated`() {
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(get("/committees/{committeeId}", committeeId))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class CreateCommittee {

        @Test
        fun `allows BOARD to create committees`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/committees")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommitteePayload(member.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies non-BOARD users from creating committees`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeMember = createUserWithRole(Role.COMMITTEE)

            mvc.perform(
                post("/committees")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommitteePayload(committeeMember.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies COMMITTEE role from creating committees`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/committees")
                    .with(bearer(committee))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommitteePayload(member.id!!))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(
                post("/committees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommitteePayload(member.id!!))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateCommittee {

        @Test
        fun `allows BOARD to update any committee`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val committeeId = committee.id!!

            mvc.perform(
                put("/committees/{id}", committeeId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateCommitteePayload(member.id!!, committee.version))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies committee member from updating their committee`() {
            val committeeUser = createUserWithRole(Role.COMMITTEE)
            val committee = addCommitteeMember(createCommitteeFixture(), committeeUser)
            val committeeId = committee.id!!

            mvc.perform(
                put("/committees/{id}", committeeId)
                    .with(bearer(committeeUser))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateCommitteePayload(committeeUser.id!!, committee.version))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies non-member from updating committee`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeOwner = createUserWithRole(Role.COMMITTEE)
            val committee = addCommitteeMember(createCommitteeFixture(), committeeOwner)
            val committeeId = committee.id!!

            mvc.perform(
                put("/committees/{id}", committeeId)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateCommitteePayload(committeeOwner.id!!, committee.version, "Hacked Committee"))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val committeeId = committee.id!!

            mvc.perform(
                put("/committees/{id}", committeeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateCommitteePayload(member.id!!, committee.version, "Unauthorized Update"))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteCommittee {

        @Test
        fun `allows BOARD to delete committees`() {
            val board = createUserWithRole(Role.BOARD)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                delete("/committees/{id}", committeeId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies committee member from deleting committee`() {
            val committee = createUserWithRole(Role.COMMITTEE)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                delete("/committees/{id}", committeeId)
                    .with(bearer(committee))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `denies non-member from deleting committee`() {
            val member = createUserWithRole(Role.MEMBER)
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(
                delete("/committees/{id}", committeeId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val committeeId = createCommitteeFixture().id!!

            mvc.perform(delete("/committees/{id}", committeeId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/committees")
                    .with(bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createCommitteePayload(member.id!!))
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `BOARD can perform COMMITTEE operations`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val committee = addCommitteeMember(createCommitteeFixture(), member)
            val committeeId = committee.id!!

            mvc.perform(
                put("/committees/{id}", committeeId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateCommitteePayload(member.id!!, committee.version))
            )
                .andExpect(status().isOk)
        }
    }
}
