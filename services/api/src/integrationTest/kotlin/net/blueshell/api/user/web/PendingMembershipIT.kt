package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class PendingMembershipIT : UserTestSupport() {
    private val board by lazy { createUserWithRole(Role.BOARD) }

    private fun start(
        member: User,
        type: String,
    ) = mvc
        .perform(
            post("/users/${member.id}/memberships")
                .with(signedIn(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id},"startDate":"${LocalDate.now()}","memberType":"$type","incasso":false}"""),
        ).andExpect(status().isCreated)

    private fun pay(
        member: User,
        periodId: Long,
    ) = mvc
        .perform(
            post("/contributions")
                .with(signedIn(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":${member.id},"contributionPeriodId":$periodId}"""),
        ).andExpect(status().is2xxSuccessful)

    private fun isMember(member: User): Boolean =
        transactionTemplate.execute { userRepository.findById(member.id!!).orElseThrow().hasRole(Role.MEMBER) }

    @Test
    fun `a new membership is pending without the member role until its first contribution, and stays active after`() {
        val applicant = createUserWithRole(Role.GUEST)
        start(applicant, "REGULAR").andExpect(jsonPath("$.pending").value(true))
        assertThat(isMember(applicant)).isFalse()
        val period = createContributionPeriodFixture()
        mvc
            .perform(get("/users/me/first-contribution").with(signedIn(applicant)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.periodId").value(period.id!!))

        pay(applicant, period.id!!)
        mvc.perform(get("/users/me/first-contribution").with(signedIn(applicant))).andExpect(status().isNoContent)
        mvc
            .perform(get("/memberships?userId=${applicant.id}").with(signedIn(board)))
            .andExpect(jsonPath("$[0].pending").value(false))
            .andExpect(jsonPath("$[0].activatedOn").value(LocalDate.now().toString()))
        assertThat(isMember(applicant)).isTrue()

        val later = createContributionPeriodFixture(LocalDate.now().plusYears(1), LocalDate.now().plusYears(2))
        pay(applicant, later.id!!)
        assertThat(isMember(applicant)).isTrue()
    }

    @Test
    fun `an honorary membership is active at once`() {
        val honoured = createUserWithRole(Role.GUEST)
        start(honoured, "HONORARY").andExpect(jsonPath("$.pending").value(false))
        assertThat(isMember(honoured)).isTrue()
    }
}
