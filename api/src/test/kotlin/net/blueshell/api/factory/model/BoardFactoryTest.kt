package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class BoardFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable board`() {
        val picture = fileWithUploader(fileFactory.createImage())
        val board = boardFactory.createFull()
        board.picture = picture
        val saved = persist(board)
        assertPersisted(net.blueshell.api.model.board.Board::class.java, saved.id)
    }
}
