package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class PresentDefinitionsTest {
    private val committees: CommitteeService = mockk()
    private val committeeMembers: CommitteeMemberService = mockk()
    private val boardMembers: BoardMemberService = mockk()
    private val users: UserService = mockk()

    private fun committee(
        id: Long,
        name: String,
        archived: Boolean = false,
    ) = Committee(name = name, description = "").apply {
        this.id = id
        this.archived = archived
    }

    private val sitecie = committee(1L, "Sitecie")
    private val oneOf = committee(2L, "One-Of-Committee (Member's Iniative)")
    private val stopped = committee(3L, "Lancie", archived = true)

    @Test
    fun `activists are everybody seated today on the board or a running committee, but not One-Of-Committee`() {
        every { committees.findAll() } returns listOf(sitecie, oneOf, stopped)
        every { committeeMembers.findUserIdsOnCommittee(1L) } returns setOf(10L)
        every { committeeMembers.findUserIdsOnCommittee(2L) } returns setOf(20L)
        every { committeeMembers.findUserIdsOnCommittee(3L) } returns setOf(30L)
        every { boardMembers.serversBetween(any(), any()) } returns setOf(40L)
        every { boardMembers.servedBetween(any(), any(), any()) } answers { firstArg<Long>() == 40L }
        every { committees.findAllByUserId(any()) } answers {
            mapOf(10L to sitecie, 20L to oneOf, 30L to stopped)[firstArg()]?.let { mutableListOf(it) } ?: mutableListOf()
        }

        val activists = ActivistsDefinition(committees, committeeMembers, boardMembers)

        assertThat(activists.members()).containsExactlyInAnyOrder(10L, 40L)
        assertThat(listOf(10L, 20L, 30L, 40L, 50L).filter(activists::contains)).containsExactly(10L, 40L)
        assertThat(activists.type.listedOnBrevo).isFalse()
        assertThat(activists.type.category()).isEqualTo(CohortCategory.MEMBERS)
    }

    @Test
    fun `members are everybody holding the member role itself, not a role above it`() {
        every { users.findIdsHoldingItself(Role.MEMBER) } returns setOf(1L)
        every { users.findById(1L) } returns Entities.user(id = 1L).apply { roles = mutableSetOf(Role.MEMBER, Role.BOARD) }
        every { users.findById(2L) } returns Entities.user(id = 2L).apply { roles = mutableSetOf(Role.BOARD) }
        every { users.findById(3L) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "gone")
        every { users.findById(4L) } throws IllegalStateException("the database did not answer")

        val members = CurrentMembersDefinition(users)

        assertThat(members.members()).containsExactly(1L)
        assertThat(listOf(1L, 2L, 3L).filter(members::contains)).containsExactly(1L)
        // Only a user who is gone is no member; a failed read must not take somebody's role away.
        assertThrows<IllegalStateException> { members.contains(4L) }
        assertThat(members.type).isEqualTo(CohortType.CURRENT_MEMBERS)
        assertThat(members.type.listedOnBrevo).isFalse()
    }

    @Test
    fun `each is one cohort`() {
        val activists = ActivistsProvider(committees, committeeMembers, boardMembers).definitions().single()
        val members = CurrentMembersProvider(users).definitions().single()

        assertThat(listOf(activists.key, activists.label, activists.scope)).containsExactly("ACTIVISTS", "Activists", null)
        assertThat(listOf(members.key, members.label, members.scope)).containsExactly("CURRENT_MEMBERS", "Members", null)
        assertThat(CohortFolders.forType(CohortType.ACTIVISTS)).isEqualTo("Activists")
        assertThat(CohortFolders.forType(CohortType.CURRENT_MEMBERS)).isEqualTo(CohortFolders.MEMBERS)
    }
}
