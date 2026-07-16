package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.persistence.repository.MemberRepository
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
    inner class Preview {

        @Test
        fun `includes users with an active membership and skips those without`() {
            val board = createUserWithRole(Role.BOARD)
            val withMembership = createUserWithRole(Role.MEMBER)
            createMembershipFixture(user = withMembership)
            val without = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/bulk/end/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(withMembership.id!!, without.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.selected").value(2))
                .andExpect(jsonPath("$.counts.willApply").value(1))
                .andExpect(jsonPath("$.counts.skipped").value(1))
        }
    }

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
