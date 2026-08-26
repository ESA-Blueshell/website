package net.blueshell.api.domain.board.application

import net.blueshell.api.domain.board.application.exception.BoardMemberNotFoundException
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.user.application.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
    ): Board {
        val board = Board(
            candidate = candidate,
            startDate = startDate,
            name = name,
            endDate = endDate,
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
    ): Board {
        val board = boardService.findById(id)
        board.name = name
        board.candidate = candidate
        board.startDate = startDate
        board.endDate = endDate
        board.replacePicture(pictureId?.let { fileService.findById(it) })
        return boardService.update(board)
    }

    @Transactional
    fun addMember(
        boardId: Long,
        userId: Long,
        role: String,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): BoardMember {
        val id = BoardMember.Id(boardId, userId)
        val exists = boardMemberService.existsById(id)
        val board = boardService.findById(boardId)
        val user = userService.findById(userId)
        return if (exists) {
            val member = boardMemberService.findById(id).apply {
                this.role = role
                this.startDate = startDate
                this.endDate = endDate
            }
            boardMemberService.update(member)
        } else {
            boardMemberService.create(
                BoardMember(
                    user = user,
                    board = board,
                    role = role,
                    startDate = startDate,
                    endDate = endDate,
                ),
            )
        }
    }

    @Transactional
    fun removeMember(boardId: Long, userId: Long) {
        boardService.findById(boardId)
        val memberId = BoardMember.Id(boardId, userId)
        if (!boardMemberService.existsById(memberId)) {
            throw BoardMemberNotFoundException(boardId, userId)
        }
        boardMemberService.deleteById(memberId)
    }
}
