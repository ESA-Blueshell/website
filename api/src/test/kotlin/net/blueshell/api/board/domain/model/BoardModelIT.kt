package net.blueshell.api.board.persistence

import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BoardModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val board = boardFactory.createBasic()
            board.name = unique("board")
            board.candidate = "Candidate"
            board.startDate = LocalDate.of(2023, 1, 1)
            board.endDate = LocalDate.of(2023, 12, 31)

            val found = persistAndReload(board, Board::class.java) { it.id }

            assertEquals(board.name, found.name)
            assertEquals(board.candidate, found.candidate)
            assertEquals(board.startDate, found.startDate)
            assertEquals(board.endDate, found.endDate)
        }

        @Test
        fun `persists picture relation when setting entity`() {
            val board = boardFactory.createBasic()
            board.name = unique("board")
            board.candidate = "Candidate"
            board.startDate = LocalDate.of(2023, 1, 1)
            board.endDate = LocalDate.of(2023, 12, 31)
            val picture = persist(fileWithUploader(fileFactory.createImage()))
            board.picture = picture

            val found = persistAndReload(board, Board::class.java) { it.id }

            assertEquals(picture.id, found.picture?.id)
        }

        @Test
        fun `persists picture relation when setting id`() {
            val board = boardFactory.createBasic()
            board.name = unique("board")
            board.candidate = "Candidate"
            board.startDate = LocalDate.of(2023, 1, 1)
            board.endDate = LocalDate.of(2023, 12, 31)
            val picture = persist(fileWithUploader(fileFactory.createImage()))
            board.picture = entityManager.getReference(net.blueshell.api.file.persistence.File::class.java, picture.id)

            val found = persistAndReload(board, Board::class.java) { it.id }

            assertEquals(picture.id, found.picture?.id)
        }
    }
}
