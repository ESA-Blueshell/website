package net.blueshell.api.integration.model.board

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.board.Board
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BoardModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_picture_relation() {
            val board = boardFactory.createBasic()
            board.name = unique("board")
            board.candidate = "Candidate"
            board.startDate = LocalDate.of(2023, 1, 1)
            board.endDate = LocalDate.of(2023, 12, 31)
            board.picture = persist(fileWithUploader(fileFactory.createImage()))

            val found = persistAndReload(board, Board::class.java) { it.id }

            assertEquals(board.name, found.name)
            assertEquals(board.candidate, found.candidate)
            assertEquals(board.startDate, found.startDate)
            assertEquals(board.endDate, found.endDate)
            assertEquals(board.picture?.id, found.picture?.id)
        }
    }
}
