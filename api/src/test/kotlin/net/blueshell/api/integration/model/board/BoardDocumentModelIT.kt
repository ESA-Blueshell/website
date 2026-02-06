package net.blueshell.api.integration.model.board

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.File
import net.blueshell.api.model.board.BoardDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BoardDocumentModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_relations() {
            val board = persist(boardFactory.createBasic())
            val documentFile: File = persist(fileWithUploader(fileFactory.createDocument()))

            val document = boardDocumentFactory.createBasic()
            document.board = board
            document.name = unique("document")
            document.file = documentFile

            val found = persistAndReload(document, BoardDocument::class.java) { it.id }

            assertEquals(board.id, found.board.id)
            assertEquals(document.name, found.name)
            assertEquals(documentFile.id, found.file.id)
        }
    }
}
