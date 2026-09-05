package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.UserErasureService
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Contract and authorisation for the two bulk contribution endpoints: which requests are
 * refused, with which code, naming which ids. What the association guarantees about the
 * rows themselves is specified in bulk-contribution-marking.feature.
 */
@SpringBootTest
class ContributionBulkControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var erasure: UserErasureService

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

    /**
     * The real erasure path, because what marks an id deleted is the `deleted_users` snapshot
     * rather than the user row: stamping `users.deleted_at` instead hides the row from
     * `existsById` and the id comes back as never having been a user.
     */
    private fun softDelete(user: User) = erasure.deleteUser(requireNotNull(user.id))

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
    fun `a selection naming a deleted user is refused with that id`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val member = memberWith(MemberType.REGULAR)
        val deleted = memberWith(MemberType.REGULAR)
        softDelete(deleted)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id, deleted.id), period.id)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("DeletedUserIds"))
            .andExpect(jsonPath("$.errors[0].field").value("userIds"))
            .andExpect(jsonPath("$.errors[0].values[0]").value(deleted.id))
    }

    @Test
    fun `every reason a selection was refused is reported together`() {
        val board = userFactory.createUserWithRole(Role.BOARD)
        val period = contributionFactory.createPeriod()
        val honorary = memberWith(MemberType.HONORARY)
        val deleted = memberWith(MemberType.REGULAR)
        softDelete(deleted)

        mvc.perform(
            post("/contributions/bulk/mark-paid")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(honorary.id, deleted.id), period.id)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder("DeletedUserIds", "HonoraryUserIds")))
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
