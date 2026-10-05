package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.board.api.BoardYear
import net.blueshell.api.cohort.persistence.CohortCategory
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
            .containsExactly(CohortType.ACTIVISTS, CohortType.CURRENT_MEMBERS, CohortType.TEAM_PLAYERS, CohortType.BOARD_YEAR_MEMBERS)
    }

    @Test
    fun `each board that has taken office keeps its people under its years`() {
        every { boardMembers.boardYearsBy(today) } returns
            listOf(BoardYear(4, LocalDate.of(2024, 9, 1), LocalDate.of(2025, 8, 31)), BoardYear(5, LocalDate.of(2025, 9, 1), null))
        every { boardMembers.everOn(4) } returns setOf(20L, 21L)
        every { boardMembers.everOn(5) } returns setOf(22L)

        val provider = BoardYearProvider(boardMembers)
        val (past, inOffice) = provider.definitions()

        assertThat(provider.type).isEqualTo(CohortType.BOARD_YEAR_MEMBERS)
        assertThat(listOf(past.key, past.label)).containsExactly("BOARD_YEAR_MEMBERS:4", "Board 2024-2025")
        assertThat(listOf(past.folder, past.scope)).containsExactly("Board", 4L)
        assertThat(past.members()).containsExactly(20L, 21L)
        assertThat(listOf(20L, 22L).map(past::contains)).containsExactly(true, false)
        // A board with no end set yet is named by the year after it took office.
        assertThat(inOffice.label).isEqualTo("Board 2025-2026")
        assertThat(CohortFolders.forType(CohortType.BOARD_YEAR_MEMBERS)).isEqualTo("Board")
    }

    @Test
    fun `the board is who serves today, and Kandi who sits on a board not yet in office`() {
        every { boardMembers.servingOn(today) } returns setOf(20L)
        every { boardMembers.candidatesOn(today) } returns setOf(30L)
        val board = BoardProvider(boardMembers).definitions().single()
        val kandi = KandiProvider(boardMembers).definitions().single()

        assertThat(board.members()).containsExactly(20L)
        assertThat(listOf(20L, 21L).map(board::contains)).containsExactly(true, false)
        assertThat(kandi.members()).containsExactly(30L)
        assertThat(listOf(30L, 20L).map(kandi::contains)).containsExactly(true, false)
        assertThat(listOf(board.key, board.label, board.folder, board.scope)).containsExactly("BOARD", "Board", "Board", null)
        assertThat(listOf(kandi.key, kandi.label, kandi.folder, kandi.scope)).containsExactly("KANDI", "Kandi", "Board", null)
        assertThat(
            listOf(BoardProvider(boardMembers).type, KandiProvider(boardMembers).type),
        ).containsExactly(CohortType.BOARD, CohortType.KANDI)
        assertThat(listOf(CohortType.BOARD, CohortType.KANDI).map { it.category() }).containsOnly(CohortCategory.MEMBERS)
        assertThat(listOf(CohortType.BOARD, CohortType.KANDI).map { CohortFolders.forType(it) }).containsOnly("Board")
    }

    private fun membership(
        start: LocalDate,
        end: LocalDate?,
    ): Membership = Entities.membership(startDate = start, endDate = end)
}
