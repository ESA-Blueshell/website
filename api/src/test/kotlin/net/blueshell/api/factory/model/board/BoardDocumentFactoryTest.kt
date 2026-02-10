package net.blueshell.api.factory.model.board

import net.blueshell.api.board.persistence.BoardDocument
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class BoardDocumentFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable board document`() {
        val board = boardFactory.createBasic()
        val document = fileWithUploader(fileFactory.createDocument())

        val boardDocument = boardDocumentFactory.createBasic()
        boardDocument.board = persist(board)
        boardDocument.file = persist(document)

        val saved = persist(boardDocument)
        assertPersisted(BoardDocument::class.java, saved.id)
    }
}
