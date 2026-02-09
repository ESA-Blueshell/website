package net.blueshell.api.board.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.file.model.File
import net.blueshell.api.board.model.BoardMember
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BoardMemberModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists board relation when setting entity`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())

            val member = boardMemberFactory.createBasic(board, user)

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(board.id, found.boardId)
            assertEquals(board.id, found.board.id)
        }

        @Test
        fun `persists board relation when setting id`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())

            val member = BoardMember()
            member.user = user
            member.boardId = board.id!!

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(board.id, found.boardId)
            assertEquals(board.id, found.board.id)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())

            val member = boardMemberFactory.createBasic(board, user)

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())

            val member = BoardMember()
            member.board = board
            member.userId = user.id!!

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user.id)
        }

        @Test
        fun `persists picture relation when setting entity`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())
            val picture: File = persist(fileWithUploader(fileFactory.createImage()))

            val member = boardMemberFactory.createBasic(board, user)
            member.picture = picture

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(picture.id, found.picture?.id)
        }

        @Test
        fun `persists picture relation when setting id`() {
            val board = persist(boardFactory.createBasic())
            val user = persist(userFactory.createBasic())
            val picture: File = persist(fileWithUploader(fileFactory.createImage()))

            val member = boardMemberFactory.createBasic(board, user)
            member.picture = entityManager.getReference(File::class.java, picture.id)

            val found = persistAndReload(member, BoardMember::class.java) { it.id }

            assertEquals(picture.id, found.picture?.id)
        }
    }
}
