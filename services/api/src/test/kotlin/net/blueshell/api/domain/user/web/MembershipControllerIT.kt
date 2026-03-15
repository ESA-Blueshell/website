package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import net.blueshell.api.shared.enums.MemberType
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
import java.time.LocalDate

@SpringBootTest
class MembershipControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var membershipRepository: MemberRepository

    private fun boardCreatePayload(
        userId: Long,
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): String =
        """{"userId":$userId,"memberType":"REGULAR","startDate":"$startDate","incasso":true}"""

    private fun updatePayload(
        userId: Long,
        version: Long,
        startDate: LocalDate = LocalDate.now().minusDays(7),
        endDate: LocalDate = LocalDate.now().plusDays(7)
    ): String =
        """{"userId":$userId,"memberType":"ALUMNI","startDate":"$startDate","endDate":"$endDate","incasso":false,"version":$version}"""

    @Nested
    inner class FindMemberships {

        @Test
        fun `lists memberships`() {
            val board = createUserWithRole(Role.BOARD)
            createMembershipFixture()

            mvc.perform(
                get("/memberships")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].id").isNumber)
        }
    }

    @Nested
    inner class CreateMembership {

        @Test
        fun `creates membership for eligible user`() {
            val guest = assignMemberProfile(assignAddress(createUserWithRole(Role.GUEST)))

            mvc.perform(
                post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(guest.id))
                .andExpect(jsonPath("$.memberType").value("REGULAR"))

            assertThat(membershipRepository.existsByUser_Id(guest.id!!)).isTrue()
        }

        @Test
        fun `returns forbidden when user already has member role`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns forbidden when address is missing`() {
            val guest = assignMemberProfile(createUserWithRole(Role.GUEST))

            mvc.perform(
                post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns forbidden when member profile is missing`() {
            val guest = assignAddress(createUserWithRole(Role.GUEST))

            mvc.perform(
                post("/memberships")
                    .with(bearer(guest))
            )
                .andExpect(status().isForbidden)
        }
    }

    @Nested
    inner class BoardCreateMembership {

        @Test
        fun `board creates membership`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users/{userId}/memberships", user.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(boardCreatePayload(user.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.memberType").value("REGULAR"))
                .andExpect(jsonPath("$.incasso").value(true))

            assertThat(membershipRepository.existsByUser_Id(user.id!!)).isTrue()
        }

        @Test
        fun `returns not found when creating membership for unknown user`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/users/{userId}/memberships", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(boardCreatePayload(999999L))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class UpdateMembership {

        @Test
        fun `updates membership`() {
            val board = createUserWithRole(Role.BOARD)
            val membership = createMembershipFixture()

            mvc.perform(
                put("/memberships/{id}", membership.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(membership.userId, membership.version))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(membership.id))
                .andExpect(jsonPath("$.userId").value(membership.userId))
                .andExpect(jsonPath("$.memberType").value("ALUMNI"))
                .andExpect(jsonPath("$.incasso").value(false))

            val updated = membershipRepository.findById(membership.id!!).orElseThrow()
            assertThat(updated.memberType).isEqualTo(MemberType.ALUMNI)
            assertThat(updated.incasso).isFalse()
        }

        @Test
        fun `returns not found when membership does not exist`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/memberships/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(user.id!!, 0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindMembershipById {

        @Test
        fun `finds membership by id`() {
            val user = createUserWithRole(Role.MEMBER)
            val membership = createMembershipFixture(user = user)

            mvc.perform(
                get("/memberships/{id}", membership.id)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(membership.id))
                .andExpect(jsonPath("$.userId").value(user.id))
        }

        @Test
        fun `returns not found when membership does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/memberships/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
