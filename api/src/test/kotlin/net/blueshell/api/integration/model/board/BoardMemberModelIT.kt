package net.blueshell.api.integration.model.board

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.File
import net.blueshell.api.model.board.BoardMember
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BoardMemberModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns_and_picture_relation() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())
            val picture: File = persist(fileWithUploader(fileFactory.createImage()))

            val member = boardMemberFactory.createBasic(board, user)
            member.picture = picture

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(board.id, found.boardId)
            assertEquals(user.id, found.userId)
            assertEquals(picture.id, found.picture?.id)
            assertEquals(board.id, found.board.id)
            assertEquals(user.id, found.user.id)
        }
    }
}
