package net.blueshell.api.board.web

import net.blueshell.api.board.domain.BoardInput
import net.blueshell.api.board.domain.BoardMemberInput
import net.blueshell.api.board.domain.BoardUseCases
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

/** Each write hands the use cases one input, mapped from the request in one expression. */
class BoardControllerWriteTest {
    private val useCases = mock<BoardUseCases>()
    private val controller = BoardController(useCases)
    private val start = LocalDate.of(2025, 9, 1)

    private val board =
        Board(number = 9, candidate = "Eeveelutions", startDate = start).also {
            it.id = 4
            it.createdAt = Instant.EPOCH
            it.updatedAt = Instant.EPOCH
        }
    private val member =
        BoardMember(board = board, role = "Chair", startDate = start).also {
            it.id = 5
            it.createdAt = Instant.EPOCH
            it.updatedAt = Instant.EPOCH
        }

    private val request = BoardRequest(number = 9, name = "Eeveelutions", startDate = start, cheer = "Go!", version = 3)
    private val input = BoardInput(9, "Eeveelutions", null, start, null, null, "Go!", null, null)

    @Test
    fun `makes and edits a board from one input, the edit carrying the version it was made against`() {
        whenever(useCases.create(any())).thenReturn(board)
        whenever(useCases.update(any(), any(), anyOrNull())).thenReturn(board)

        controller.createBoard(request)
        controller.updateBoard(4, request)

        verify(useCases).create(input)
        verify(useCases).update(4, input, 3)
    }

    @Test
    fun `puts somebody on a board and edits their place from one input`() {
        val place = BoardMemberInput("Chair", start, null, "Sanne Kok", null, null, null)
        whenever(useCases.addMember(any(), anyOrNull(), any())).thenReturn(member)
        whenever(useCases.updateMember(any(), any())).thenReturn(member)

        controller.addMember(4, AddBoardMemberRequest(userId = 8, role = "Chair", startDate = start, displayName = "Sanne Kok"))
        val answer = controller.updateMember(4, 5, UpdateBoardMemberRequest(role = "Chair", startDate = start, displayName = "Sanne Kok"))

        verify(useCases).addMember(eq(4), eq(8), eq(place))
        verify(useCases).updateMember(5, place)
        assertThat(answer.id).isEqualTo(5)
    }
}
