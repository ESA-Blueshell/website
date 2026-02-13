package net.blueshell.api.factory.model.board

import com.github.javafaker.Faker
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.factory.model.UserFactory
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
        boardMember.board = board
        boardMember.user = user
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
