package net.blueshell.api.board.domain.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.file.domain.model.File
import net.blueshell.api.board.domain.model.Board
import net.blueshell.api.board.domain.model.BoardDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BoardDocumentModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = board
            document.name = unique("document")
            document.file = documentFile

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(document.name, found.name)
        }

        @Test
        fun `persists board relation when setting entity`() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = board
            document.name = unique("document")
            document.file = documentFile

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(board.id, found.board.id)
        }

        @Test
        fun `persists board relation when setting id`() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = entityManager.getReference(Board::class.java, board.id)
            document.name = unique("document")
            document.file = documentFile

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(board.id, found.board.id)
        }

        @Test
        fun `persists file relation when setting entity`() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = board
            document.name = unique("document")
            document.file = documentFile

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(documentFile.id, found.file.id)
        }

        @Test
        fun `persists file relation when setting id`() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = board
            document.name = unique("document")
            document.file = entityManager.getReference(File::class.java, documentFile.id)

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(documentFile.id, found.file.id)
        }
    }
}
