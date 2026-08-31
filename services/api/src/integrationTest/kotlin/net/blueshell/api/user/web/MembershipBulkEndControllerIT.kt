package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Ending the memberships of a selection. The last test is the one that matters most:
 * preview and execute are supposed to be the same decision read twice, so it plays the
 * same selection through both and holds their verdicts against each other.
 */
@SpringBootTest
class MembershipBulkEndControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var membershipRepository: MemberRepository

    private fun body(userIds: List<Long?>) = """{"userIds":[${userIds.joinToString(",")}]}"""

    @Test
    fun `ending the memberships of a selection`() {
        val board = createUserWithRole(Role.BOARD)
        val first = createMembershipFixture()
        val second = createMembershipFixture()

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(first.userId, second.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(2))
            .andExpect(jsonPath("$.skipped").value(0))

        assertThat(membershipRepository.findById(first.id!!).orElseThrow().endDate).isEqualTo(LocalDate.now())
        assertThat(membershipRepository.findById(second.id!!).orElseThrow().endDate).isEqualTo(LocalDate.now())
    }

    @Test
    fun `a member with no active membership is skipped rather than ended`() {
        val board = createUserWithRole(Role.BOARD)
        val ended = createMembershipFixture(endDate = LocalDate.now().minusDays(1))

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(ended.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(0))
            .andExpect(jsonPath("$.skipped").value(1))

        assertThat(membershipRepository.findById(ended.id!!).orElseThrow().endDate)
            .isEqualTo(LocalDate.now().minusDays(1))
    }

    @Test
    fun `the preview names every selected member and says why each one is skipped`() {
        val board = createUserWithRole(Role.BOARD)
        val active = createMembershipFixture()
        val ended = createMembershipFixture(endDate = LocalDate.now().minusDays(1))
        val startedToday = createMembershipFixture(startDate = LocalDate.now())

        mvc.perform(
            post("/memberships/bulk/end/preview")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(active.userId, ended.userId, startedToday.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.effectiveDate").value(LocalDate.now().toString()))
            .andExpect(jsonPath("$.rows.length()").value(3))
            .andExpect(jsonPath("$.rows[0].userId").value(active.userId))
            .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
            .andExpect(jsonPath("$.rows[1].disposition").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[1].reason").value("NO_ACTIVE_MEMBERSHIP"))
            .andExpect(jsonPath("$.rows[2].disposition").value("SKIPPED"))
            .andExpect(jsonPath("$.rows[2].reason").value("STARTED_TODAY"))
    }

    @Test
    fun `a membership that started today is left alone`() {
        val board = createUserWithRole(Role.BOARD)
        val startedToday = createMembershipFixture(startDate = LocalDate.now())

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(startedToday.userId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(0))
            .andExpect(jsonPath("$.skipped").value(1))

        assertThat(membershipRepository.findById(startedToday.id!!).orElseThrow().endDate).isNull()
    }

    @Test
    fun `a selection naming an id that was never a user is refused`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createMembershipFixture()
        val missingId = member.userId + 999_999

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.userId, missingId))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errors[0].code").value("UnknownUserIds"))
            .andExpect(jsonPath("$.errors[0].field").value("userIds"))
            .andExpect(jsonPath("$.errors[0].values[0]").value(missingId))

        assertThat(membershipRepository.findById(member.id!!).orElseThrow().endDate).isNull()
    }

    @Test
    fun `the preview and the apply agree over the same selection`() {
        val board = createUserWithRole(Role.BOARD)
        val active = createMembershipFixture()
        val ended = createMembershipFixture(endDate = LocalDate.now().minusDays(1))
        val startedToday = createMembershipFixture(startDate = LocalDate.now())
        val selection = listOf(active.userId, ended.userId, startedToday.userId)

        val preview = mvc.perform(
            post("/memberships/bulk/end/preview")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val dispositions = Regex("\"disposition\":\"(\\w+)\"").findAll(preview).map { it.groupValues[1] }.toList()
        val included = dispositions.count { it == "INCLUDED" }
        val skipped = dispositions.count { it != "INCLUDED" }

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(included))
            .andExpect(jsonPath("$.skipped").value(skipped))
    }

    /**
     * The role follows the whole set rather than the row that changed: an earlier spell
     * that is already closed is no reason to keep the role, and the closed row itself is
     * left alone.
     */
    @Test
    fun `the member role is recomputed from the whole set once the last active spell ends`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val firstSpell = createMembershipFixture(
            user = member,
            startDate = LocalDate.now().minusYears(5),
            endDate = LocalDate.now().minusYears(4),
        )
        val secondSpell = createMembershipFixture(user = member, startDate = LocalDate.now().minusYears(3))

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(member.id))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applied").value(1))

        assertThat(refreshUser(member).roles).doesNotContain(Role.MEMBER)
        assertThat(membershipRepository.findById(secondSpell.id!!).orElseThrow().endDate).isEqualTo(LocalDate.now())
        assertThat(membershipRepository.findById(firstSpell.id!!).orElseThrow().endDate)
            .isEqualTo(LocalDate.now().minusYears(4))
    }

    @Test
    fun `a member cannot end memberships in bulk`() {
        val member = createUserWithRole(Role.MEMBER)
        val victim = createMembershipFixture()

        mvc.perform(
            post("/memberships/bulk/end")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(listOf(victim.userId))),
        )
            .andExpect(status().isForbidden)

        assertThat(membershipRepository.findById(victim.id!!).orElseThrow().endDate).isNull()
    }
}
