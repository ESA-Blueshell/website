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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class MembershipBulkEndControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private fun body(userIds: List<Long>) = """{"userIds":[${userIds.joinToString(",")}]}"""

    @Nested
    inner class Execute {

        @Test
        fun `ends active memberships effective today and skips users without one`() {
            val board = createUserWithRole(Role.BOARD)
            val withMembership = createUserWithRole(Role.MEMBER)
            val membership = createMembershipFixture(user = withMembership)
            val without = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/bulk/end/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(withMembership.id!!, without.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(1))

            val ended = memberRepository.findById(membership.id!!).orElseThrow()
            assertThat(ended.endDate).isEqualTo(LocalDate.now())
        }

        @Test
        fun `protected users keep their membership - committee, board, admin and honorary are skipped`() {
            val board = createUserWithRole(Role.BOARD)

            val committee = createUserWithRole(Role.COMMITTEE)
            val committeeMembership = createMembershipFixture(user = committee)
            val boardUser = createUserWithRole(Role.BOARD)
            val boardMembership = createMembershipFixture(user = boardUser)
            val admin = createUserWithRole(Role.ADMIN)
            val adminMembership = createMembershipFixture(user = admin)
            val honorary = createUserWithRole(Role.MEMBER)
            val honoraryMembership = createMembershipFixture(user = honorary, memberType = MemberType.HONORARY)

            mvc.perform(
                post("/memberships/bulk/end/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(committee.id!!, boardUser.id!!, admin.id!!, honorary.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(4))

            // None of the protected memberships were ended.
            listOf(committeeMembership, boardMembership, adminMembership, honoraryMembership).forEach {
                val reloaded = memberRepository.findById(it.id!!).orElseThrow()
                assertThat(reloaded.endDate).isNull()
            }
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/bulk/end/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!)))
            )
                .andExpect(status().isForbidden)
        }
    }
}
