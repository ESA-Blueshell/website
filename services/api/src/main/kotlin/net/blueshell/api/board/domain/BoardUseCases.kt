package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.file.api.FileService
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.board.api.BoardMemberService

/**
 * Board operations that touch more than one collaborator. Reads and deletes go
 * straight to [BoardService], whose base class already carries the transaction.
 */
@Service
class BoardUseCases(
    private val boardService: BoardService,
    private val boardMemberService: BoardMemberService,
    private val fileService: FileService,
    private val userService: UserService,
) {
    @Transactional
    fun create(
        name: String,
        candidate: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        pictureId: Long?,
        image: String? = null,
    ): Board {
        val board = Board(
            candidate = candidate,
            startDate = startDate,
            name = name,
            endDate = endDate,
            image = image,
        )
        pictureId?.let { board.replacePicture(fileService.findById(it)) }
        return boardService.create(board)
    }

    // `version` is deliberately absent: the command carried one and the handler
    // never applied it, so board update has never used optimistic locking.
    @Transactional
    fun update(
        id: Long,
        name: String,
        candidate: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        pictureId: Long?,
        image: String? = null,
    ): Board {
        val board = boardService.findById(id)
        board.name = name
        board.candidate = candidate
        board.startDate = startDate
        board.endDate = endDate
        board.image = image
        board.replacePicture(pictureId?.let { fileService.findById(it) })
        return boardService.update(board)
    }

    /**
     * Seats somebody on a board. [userId] is absent for the people most of the history is
     * made of, who never had an account here: their seat stands under [displayName].
     *
     * A member already seated on this board keeps their seat and has it updated; a seat with
     * no account is always a new one, since there is nothing to match it on.
     */
    @Transactional
    fun addMember(
        boardId: Long,
        userId: Long?,
        role: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        displayName: String? = null,
        description: String? = null,
        image: String? = null,
    ): BoardMember {
        val board = boardService.findById(boardId)
        val user = userId?.let { userService.findById(it) }
        val existing = userId?.let { boardMemberService.findByBoardAndUser(boardId, it) }

        if (existing != null) {
            existing.role = role
            existing.startDate = startDate
            existing.endDate = endDate
            existing.displayName = displayName
            existing.description = description
            existing.image = image
            return boardMemberService.update(existing)
        }

        return boardMemberService.create(
            BoardMember(
                board = board,
                user = user,
                role = role,
                startDate = startDate,
                endDate = endDate,
                displayName = displayName,
                description = description,
                image = image,
            ),
        )
    }

    @Transactional
    fun updateMember(
        id: Long,
        role: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        displayName: String? = null,
        description: String? = null,
        image: String? = null,
    ): BoardMember {
        val seat = boardMemberService.findSeat(id)
        seat.role = role
        seat.startDate = startDate
        seat.endDate = endDate
        seat.displayName = displayName
        seat.description = description
        seat.image = image
        return boardMemberService.update(seat)
    }

    /** A null member detaches the seat, which keeps standing under its own name. */
    @Transactional
    fun linkMember(id: Long, userId: Long?): BoardMember {
        val seat = boardMemberService.findSeat(id)
        seat.user = userId?.let { userService.findById(it) }
        return boardMemberService.update(seat)
    }

    @Transactional
    fun removeMember(id: Long) {
        // Looked up first so a seat that is not there answers 404 rather than silently nothing.
        boardMemberService.findSeat(id)
        boardMemberService.deleteById(id)
    }
}
