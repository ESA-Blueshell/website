package net.blueshell.api.board.web

import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Removing a board over http, refused while it still has members.
 *
 * A board cascades every write to its members, so this refusal is what stops one delete from
 * soft-deleting a whole year of people. Asserted at the http seam, because that is where a
 * caller meets the reason.
 */
@SpringBootTest
class BoardRefusalIT : UserTestSupport() {

    @Autowired
    private lateinit var boards: BoardRepository

    @Autowired
    private lateinit var members: BoardMemberRepository

    @Test
    fun `a board with members on it answers BoardHoldsMembers, its number and how many members`() {
        val boardUser = createUserWithRole(Role.BOARD)
        var board = createBoardFixture()
        board = addBoardMember(board, createUserWithRole(Role.MEMBER), "Chair")
        board = addBoardMember(board, createUserWithRole(Role.MEMBER), "Secretary")

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(boardUser)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("BoardHoldsMembers"))
            .andExpect(jsonPath("$.number").value(board.number))
            .andExpect(jsonPath("$.members").value(2))
            .andExpect(jsonPath("$.detail").value("That board cannot be removed."))
    }

    @Test
    fun `a member with no account is in the way, and one member is counted singly`() {
        val boardUser = createUserWithRole(Role.BOARD)
        val board = createBoardFixture()
        addBoardMemberWithoutAccount(board, "Thijs Lieverse", role = "Chair")

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(boardUser)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("BoardHoldsMembers"))
            .andExpect(jsonPath("$.members").value(1))
    }

    @Test
    fun `a refused removal leaves the board and every one of its members standing`() {
        val boardUser = createUserWithRole(Role.BOARD)
        var board = createBoardFixture()
        board = addBoardMember(board, createUserWithRole(Role.MEMBER), "Chair")
        board = addBoardMember(board, createUserWithRole(Role.MEMBER), "Treasurer")
        val memberIds = board.members.map { it.id!! }

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(boardUser)))
            .andExpect(status().isConflict)

        assertThat(boards.existsById(board.id!!)).isTrue()
        assertThat(members.countByBoardId(board.id!!)).isEqualTo(2)
        memberIds.forEach { assertThat(members.findById(it)).isPresent }
    }

    @Test
    fun `a board with no members removes cleanly and without a question`() {
        val boardUser = createUserWithRole(Role.BOARD)
        val board = createBoardFixture()

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(boardUser)))
            .andExpect(status().isNoContent)

        assertThat(boards.existsById(board.id!!)).isFalse()
    }

    @Test
    fun `a board emptied of its members then removes`() {
        val boardUser = createUserWithRole(Role.BOARD)
        val board = createBoardFixture()
        val member = addBoardMemberWithoutAccount(board, "Thijs Lieverse")

        mvc.perform(delete("/boards/{boardId}/members/{id}", board.id, member.id).with(bearer(boardUser)))
            .andExpect(status().isNoContent)

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(boardUser)))
            .andExpect(status().isNoContent)

        assertThat(boards.existsById(board.id!!)).isFalse()
    }

    @Test
    fun `a board that is not there answers not found rather than a refusal`() {
        val boardUser = createUserWithRole(Role.BOARD)

        mvc.perform(delete("/boards/{id}", 999999L).with(bearer(boardUser)))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a member is refused before the members are ever counted, so hiding is not the guard`() {
        val member = createUserWithRole(Role.MEMBER)
        val board = createBoardFixture()
        addBoardMemberWithoutAccount(board, "Thijs Lieverse")

        mvc.perform(delete("/boards/{id}", board.id).with(bearer(member)))
            .andExpect(status().isForbidden)

        assertThat(boards.existsById(board.id!!)).isTrue()
    }

    @Test
    fun `an unauthenticated caller is refused an empty board too`() {
        val board = createBoardFixture()

        mvc.perform(delete("/boards/{id}", board.id))
            .andExpect(status().isUnauthorized)

        assertThat(boards.existsById(board.id!!)).isTrue()
    }
}
