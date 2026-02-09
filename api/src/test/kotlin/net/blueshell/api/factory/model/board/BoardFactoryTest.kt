package net.blueshell.api.factory.model.board

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.board.persistence.Board
import org.junit.jupiter.api.Test

class BoardFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable board`() {
        val picture = fileWithUploader(fileFactory.createImage())
        val board = boardFactory.createFull()
        board.picture = picture
        val saved = persist(board)
        assertPersisted(Board::class.java, saved.id)
    }

    @Test
    fun `creates persistable current board`() {
        val board = boardFactory.createCurrent()
        board.picture = board.picture?.let { fileWithUploader(it) }
        val saved = persist(board)
        assertPersisted(Board::class.java, saved.id)
    }

    @Test
    fun `creates persistable past board`() {
        val board = boardFactory.createPast()
        board.picture = board.picture?.let { fileWithUploader(it) }
        val saved = persist(board)
        assertPersisted(Board::class.java, saved.id)
    }
}
