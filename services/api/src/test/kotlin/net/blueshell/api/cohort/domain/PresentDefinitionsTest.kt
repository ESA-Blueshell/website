package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.persistence.Membership
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PresentDefinitionsTest {
    private val committees: CommitteeService = mockk()
    private val committeeMembers: CommitteeMemberService = mockk()
    private val boardMembers: BoardMemberService = mockk()
    private val memberships: MembershipService = mockk()
    private val today = LocalDate.now()

    private fun committee(
        id: Long,
        name: String,
        archived: Boolean = false,
    ) = Committee(name = name, description = "").apply {
        this.id = id
        this.archived = archived
    }

    @Test
    fun `activists hold a committee or board seat today, but not a One-Of-Committee seat`() {
        every { committees.findAll() } returns
            listOf(committee(1L, "Sitecie"), committee(2L, "one-of-committee"), committee(3L, "Old", archived = true))
        every { committeeMembers.findUserIdsOnCommittee(1L) } returns setOf(10L)
        every { boardMembers.serversBetween(today, today) } returns setOf(20L)
        val activists = ActivistsProvider(committees, committeeMembers, boardMembers).definitions().single()

        assertThat(activists.members()).containsExactlyInAnyOrder(10L, 20L)
        assertThat(activists.contains(20L)).isTrue()
        assertThat(activists.contains(30L)).isFalse()
        assertThat(listOf(activists.key, activists.label, activists.folder)).containsExactly("ACTIVISTS", "Activists", "Activists")
        assertThat(activists.scope).isNull()
    }

    @Test
    fun `members hold an active membership that covers today, and a pending one does not count`() {
        every { memberships.findActiveUserIdsOn(today) } returns setOf(5L)
        every { memberships.findByUserId(5L) } returns mutableListOf(membership(today.minusYears(1), null))
        every { memberships.findByUserId(6L) } returns mutableListOf(membership(today.minusYears(2), today.minusDays(1)))
        every { memberships.findByUserId(7L) } returns mutableListOf(membership(today.plusDays(1), null))
        every { memberships.findByUserId(8L) } returns mutableListOf(membership(today.minusDays(3), today))
        every { memberships.findByUserId(9L) } returns
            mutableListOf(Entities.membership(startDate = today.minusDays(3), activatedOn = null))
        val members = CurrentMembersProvider(memberships).definitions().single()

        assertThat(members.members()).containsExactly(5L)
        assertThat(listOf(5L, 6L, 7L, 8L, 9L).map(members::contains)).containsExactly(true, false, false, true, false)
        assertThat(listOf(members.key, members.label, members.folder)).containsExactly("CURRENT_MEMBERS", "Members", "Members")
        assertThat(members.scope).isNull()
    }

    @Test
    fun `neither is listed on Brevo, nor is a team, and both browse under members`() {
        assertThat(CohortType.entries.filterNot { it.listedOnBrevo })
            .containsExactly(CohortType.ACTIVISTS, CohortType.CURRENT_MEMBERS, CohortType.TEAM_PLAYERS)
    }

    private fun membership(
        start: LocalDate,
        end: LocalDate?,
    ): Membership = Entities.membership(startDate = start, endDate = end)
}
