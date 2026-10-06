package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.board.api.BoardYear
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PresentDefinitionsTest {
    private val rosters: TeamRosterService = mockk()
    private val boardMembers: BoardMemberService = mockk()
    private val users: UserService = mockk()
    private val today = LocalDate.now()

    @Test
    fun `activists are the active members, who hold the COMMITTEE role or play on a current line-up`() {
        every { users.findIdsHolding(Role.COMMITTEE) } returns setOf(10L, 11L)
        every { rosters.teamNames() } returns mapOf(1L to "Valorant A", 2L to "Chess")
        every { rosters.currentPlayersOf(1L) } returns setOf(11L, 20L)
        every { rosters.currentPlayersOf(2L) } returns setOf(21L)
        val activists = ActivistsProvider(users, rosters).definitions().single()

        assertThat(activists.members()).containsExactlyInAnyOrder(10L, 11L, 20L, 21L)
        assertThat(listOf(20L, 30L).map(activists::contains)).containsExactly(true, false)
        assertThat(listOf(activists.key, activists.label, activists.folder)).containsExactly("ACTIVISTS", "Activists", "Activists")
        assertThat(activists.scope).isNull()
    }

    @Test
    fun `members are who holds the MEMBER role, and committee members who holds the COMMITTEE role`() {
        every { users.findIdsHolding(Role.MEMBER) } returns setOf(5L, 6L)
        every { users.findIdsHolding(Role.COMMITTEE) } returns setOf(6L, 7L)
        val members = CurrentMembersProvider(users).definitions().single()
        val committee = CurrentCommitteeMembersProvider(users).definitions().single()

        assertThat(members.members()).containsExactlyInAnyOrder(5L, 6L)
        assertThat(listOf(5L, 7L).map(members::contains)).containsExactly(true, false)
        assertThat(listOf(members.key, members.label, members.folder)).containsExactly("CURRENT_MEMBERS", "Members", "Members")
        assertThat(committee.members()).containsExactlyInAnyOrder(6L, 7L)
        assertThat(listOf(7L, 5L).map(committee::contains)).containsExactly(true, false)
        assertThat(listOf(committee.key, committee.label, committee.folder))
            .containsExactly("CURRENT_COMMITTEE_MEMBERS", "Committee members", "Committees")
        assertThat(listOf(members.scope, committee.scope)).containsOnlyNulls()
        assertThat(CurrentCommitteeMembersProvider(users).type).isEqualTo(CohortType.CURRENT_COMMITTEE_MEMBERS)
    }

    @Test
    fun `the activists, the teams, the esports team members and the board years are not listed on Brevo`() {
        assertThat(CohortType.entries.filterNot { it.listedOnBrevo })
            .containsExactly(CohortType.ACTIVISTS, CohortType.TEAM_PLAYERS, CohortType.CURRENT_TEAM_PLAYERS, CohortType.BOARD_YEAR_MEMBERS)
        assertThat(listOf(CohortType.CURRENT_MEMBERS, CohortType.CURRENT_COMMITTEE_MEMBERS).map { it.category() })
            .containsOnly(CohortCategory.MEMBERS)
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
        assertThat(listOf(past.folder, past.scope)).containsExactly("Boards", 4L)
        assertThat(past.members()).containsExactly(20L, 21L)
        assertThat(listOf(20L, 22L).map(past::contains)).containsExactly(true, false)
        // A board with no end set yet is named by the year after it took office.
        assertThat(inOffice.label).isEqualTo("Board 2025-2026")
        assertThat(CohortFolders.forType(CohortType.BOARD_YEAR_MEMBERS)).isEqualTo("Boards")
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
        assertThat(listOf(board.key, board.label, board.folder, board.scope)).containsExactly("BOARD", "Board", "Boards", null)
        assertThat(listOf(kandi.key, kandi.label, kandi.folder, kandi.scope)).containsExactly("KANDI", "Kandi", "Boards", null)
        assertThat(
            listOf(BoardProvider(boardMembers).type, KandiProvider(boardMembers).type),
        ).containsExactly(CohortType.BOARD, CohortType.KANDI)
        assertThat(listOf(CohortType.BOARD, CohortType.KANDI).map { it.category() }).containsOnly(CohortCategory.MEMBERS)
        assertThat(listOf(CohortType.BOARD, CohortType.KANDI).map { CohortFolders.forType(it) }).containsOnly("Boards")
    }
}
