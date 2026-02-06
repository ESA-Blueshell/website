package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class BoardMemberFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable board member`() {
        val board = persist(boardFactory.createBasic())
        val user = persistUser()
        val picture = fileWithUploader(fileFactory.createImage())

        val member = boardMemberFactory.createBasic(board, user)
        member.picture = persist(picture)

        val saved = persist(member)
        assertPersisted(net.blueshell.api.model.board.BoardMember::class.java, saved.id)
    }
}
