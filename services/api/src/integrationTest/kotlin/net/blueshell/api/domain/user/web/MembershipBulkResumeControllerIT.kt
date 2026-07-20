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
class MembershipBulkResumeControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private fun body(userIds: List<Long>) = """{"userIds":[${userIds.joinToString(",")}]}"""

    @Nested
    inner class Execute {

        @Test
        fun `resumes membership by clearing endDate`() {
            val board = createUserWithRole(Role.BOARD)
            val periodStart = LocalDate.now().minusDays(15)
            val periodEnd = LocalDate.now().plusDays(345)
            createContributionPeriodFixture(startDate = periodStart, endDate = periodEnd)

            val member = createUserWithRole(Role.MEMBER)
            val membership = createMembershipFixture(
                user = member,
                memberType = MemberType.REGULAR,
                startDate = LocalDate.now().minusDays(100),
                endDate = LocalDate.now().minusDays(5), // within basis period
            )

            mvc.perform(
                post("/memberships/bulk/resume/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))

            val resumed = memberRepository.findById(membership.id!!).orElseThrow()
            assertThat(resumed.endDate).isNull()
        }

        @Test
        fun `inserts new membership for user with no resumable membership`() {
            val board = createUserWithRole(Role.BOARD)
            val periodStart = LocalDate.now().minusDays(15)
            val periodEnd = LocalDate.now().plusDays(345)
            createContributionPeriodFixture(startDate = periodStart, endDate = periodEnd)

            // Member has no membership at all
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/bulk/resume/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))

            val newMemberships = memberRepository.findByUser_Id(member.id!!)
            assertThat(newMemberships).hasSize(1)
            val newMembership = newMemberships.first()
            assertThat(newMembership.endDate).isNull()
            assertThat(newMembership.startDate).isEqualTo(LocalDate.now())
            assertThat(newMembership.memberType).isEqualTo(MemberType.REGULAR)
            assertThat(newMembership.incasso).isFalse()
        }

        @Test
        fun `skips already-active membership`() {
            val board = createUserWithRole(Role.BOARD)
            val periodStart = LocalDate.now().minusDays(15)
            val periodEnd = LocalDate.now().plusDays(345)
            createContributionPeriodFixture(startDate = periodStart, endDate = periodEnd)

            val member = createUserWithRole(Role.MEMBER)
            createMembershipFixture(user = member, endDate = null)

            mvc.perform(
                post("/memberships/bulk/resume/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
        }
    }

    @Nested
    inner class Invariant {

        // preview.willApply == execute.applied for an unchanged DB — the regression net
        // against preview/execute divergence. classifyUser is shared, so the resume +
        // start-new rows the preview reports must all be applied by execute.
        // See docs/proposals/bulk-actions/REDESIGN.md §7.
        @Test
        fun `preview willApply equals execute applied for a mixed resume selection`() {
            val board = createUserWithRole(Role.BOARD)
            val periodStart = LocalDate.now().minusDays(15)
            val periodEnd = LocalDate.now().plusDays(345)
            createContributionPeriodFixture(startDate = periodStart, endDate = periodEnd)

            val resumable = createUserWithRole(Role.MEMBER)
            createMembershipFixture(
                user = resumable,
                memberType = MemberType.REGULAR,
                startDate = LocalDate.now().minusDays(100),
                endDate = LocalDate.now().minusDays(5), // within basis period → WILL_RESUME
            )
            val startNew = createUserWithRole(Role.MEMBER) // no membership → WILL_START_NEW
            val active = createUserWithRole(Role.MEMBER)
            createMembershipFixture(user = active, endDate = null) // ALREADY_ACTIVE → skipped

            val userIds = listOf(resumable.id!!, startNew.id!!, active.id!!)

            mvc.perform(
                post("/memberships/bulk/resume/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(userIds))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.willApply").value(2))
                .andExpect(jsonPath("$.counts.skipped").value(1))

            mvc.perform(
                post("/memberships/bulk/resume/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(userIds))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberships/bulk/resume/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!)))
            )
                .andExpect(status().isForbidden)
        }
    }
}
