package net.blueshell.api.factory.model.board

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.feature.board.model.BoardMember
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
        assertPersisted(BoardMember::class.java, saved.id)
    }
}
