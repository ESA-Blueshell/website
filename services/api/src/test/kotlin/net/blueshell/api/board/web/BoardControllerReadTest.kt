package net.blueshell.api.board.web

import net.blueshell.api.board.domain.BoardUseCases
import net.blueshell.api.board.persistence.Board
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BoardControllerReadTest {
    private val useCases = mock<BoardUseCases>()
    private val controller = BoardController(useCases)

    private val board =
        Board(number = 9, candidate = "Eeveelutions", startDate = LocalDate.of(2025, 9, 1), name = "Eeveelutions").also {
            it.id = 4
            it.createdAt = Instant.EPOCH
            it.updatedAt = Instant.EPOCH
        }

    @Test
    fun `reads every board through the use cases`() {
        whenever(useCases.all()).thenReturn(listOf(board))

        assertThat(controller.findAllBoards().map { it.number }).containsExactly(9)
    }

    @Test
    fun `reads one board through the use cases`() {
        whenever(useCases.byId(4)).thenReturn(board)

        assertThat(controller.findBoardById(4).name).isEqualTo("Eeveelutions")
    }
}
