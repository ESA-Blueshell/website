package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.model.User
import net.blueshell.api.model.board.Board
import net.blueshell.api.model.board.BoardMember
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for BoardMember model test instances.
 */
@Component
class BoardMemberFactory(
    private val faker: Faker,
    private val boardFactory: BoardFactory,
    private val userFactory: UserFactory,
    private val fileFactory: FileFactory
) {

    fun createBasic(board: Board, user: User): BoardMember {
        val boardMember = BoardMember()
        val picture = fileFactory.createImage()
        boardMember.board = board
        boardMember.user = user
        boardMember.picture = picture
        return boardMember
    }

    fun createFull(board: Board, user: User): BoardMember = createBasic(board, user)

    fun createWithCustomizations(board: Board, user: User, customizer: Consumer<BoardMember>): BoardMember {
        val boardMember = createFull(board, user)
        customizer.accept(boardMember)
        return boardMember
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
