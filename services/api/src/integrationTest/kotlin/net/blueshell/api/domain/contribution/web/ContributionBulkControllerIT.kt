package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Contract and authorisation for the two bulk contribution endpoints. Test names mirror
 * the scenario names in bulk-contribution-marking.feature so the correspondence between
 * the specification and this suite can be checked by eye.
 */
@SpringBootTest
class ContributionBulkControllerIT : UserTestSupport() {

    private fun body(userIds: List<Long?>, periodId: Long?) =
        """{"userIds":[${userIds.joinToString(",")}],"contributionPeriodId":$periodId}"""

    private fun memberWith(memberType: MemberType): User {
        val user = userFactory.createUserWithRole(Role.MEMBER)
        userFactory.createMembership(user, memberType = memberType)
        return user
    }

    private fun recordPaid(user: User, period: ContributionPeriod) = persist(
        Contribution(id = Contribution.Id(user.id, period.id), user = user, contributionPeriod = period),
    )

    @Test
    fun `recording contributions for a selection of members`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val first = memberWith(MemberType.REGULAR)
        val second = memberWith(MemberType.REGULAR)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(first.id, second.id), period.id)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(2))
            .andExpect(jsonPath("$.skipped").value(0))
    }

    @Test
    fun `a member already paid is reported as unchanged rather than applied`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val member = memberWith(MemberType.REGULAR)
        recordPaid(member, period)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id), period.id)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(0))
            .andExpect(jsonPath("$.skipped").value(1))
    }

    @Test
    fun `removing contributions for a selection of members`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val member = memberWith(MemberType.REGULAR)
        recordPaid(member, period)

        mvc.perform(
            post("/contributions/bulk/mark-unpaid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id), period.id)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(1))
    }

    @Test
    fun `a selection naming an id that was never a user is refused`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val member = memberWith(MemberType.REGULAR)
        val missingId = requireNotNull(member.id) + 999_999

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id, missingId), period.id)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("UnknownUserIds"))
            .andExpect(jsonPath("$.errors[0].field").value("userIds"))
            .andExpect(jsonPath("$.errors[0].values[0]").value(missingId))
    }

    @Test
    fun `a selection naming an honorary member is refused with that id`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val honorary = memberWith(MemberType.HONORARY)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(honorary.id), period.id)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("HonoraryUserIds"))
            .andExpect(jsonPath("$.errors[0].values[0]").value(honorary.id))
    }

    @Test
    fun `a selection naming a period that no longer exists is refused`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val member = memberWith(MemberType.REGULAR)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id), 999_999L)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("UnknownContributionPeriodId"))
            .andExpect(jsonPath("$.errors[0].field").value("contributionPeriodId"))
    }

    @Test
    fun `an empty selection is refused as a bad request`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(emptyList(), period.id)),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a member without permission cannot record contributions`() {
        val member = userFactory.createUserWithRole(Role.MEMBER)
        val period = contributionFactory.createPeriod()
        val target = memberWith(MemberType.REGULAR)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(target.id), period.id)),
        )
            .andExpect(status().isForbidden)
    }
}
