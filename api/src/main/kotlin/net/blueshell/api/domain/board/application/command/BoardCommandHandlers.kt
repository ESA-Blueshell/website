package net.blueshell.api.domain.board.application.command

import net.blueshell.api.domain.board.application.BoardMemberService
import net.blueshell.api.domain.board.application.BoardService
import net.blueshell.api.domain.board.application.exception.BoardMemberNotFoundException
import net.blueshell.api.domain.board.command.*
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateBoardHandler(
    private val boardService: BoardService,
    private val fileService: FileService
) : CommandHandler<CreateBoardCommand, Board> {
    override val commandType = CreateBoardCommand::class

    @Transactional
    override fun handle(command: CreateBoardCommand): Board {
        val board = Board()
        board.name = command.name
        board.candidate = command.candidate
        board.startDate = command.startDate
        board.endDate = command.endDate

        command.pictureId?.let { pictureId ->
            val picture = fileService.findById(pictureId)
            board.replacePicture(picture)
        }

        return boardService.create(board)
    }
}

@Component
class UpdateBoardHandler(
    private val boardService: BoardService,
    private val fileService: FileService
) : CommandHandler<UpdateBoardCommand, Board> {
    override val commandType = UpdateBoardCommand::class

    @Transactional
    override fun handle(command: UpdateBoardCommand): Board {
        val board = boardService.findById(command.id)
        board.name = command.name
        board.candidate = command.candidate
        board.startDate = command.startDate
        board.endDate = command.endDate

        command.pictureId?.let { pictureId ->
            val picture = fileService.findById(pictureId)
            board.replacePicture(picture)
        } ?: run {
            board.replacePicture(null)
        }

        return boardService.update(board)
    }
}

@Component
class FindBoardByIdHandler(
    private val boardService: BoardService
) : CommandHandler<FindBoardByIdCommand, Board> {
    override val commandType = FindBoardByIdCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: FindBoardByIdCommand): Board {
        return boardService.findById(command.id)
    }
}

@Component
class FindBoardsHandler(
    private val boardService: BoardService
) : CommandHandler<FindBoardsCommand, MutableList<Board>> {
    override val commandType = FindBoardsCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: FindBoardsCommand): MutableList<Board> {
        return boardService.findAll()
    }
}

@Component
class DeleteBoardByIdHandler(
    private val boardService: BoardService
) : CommandHandler<DeleteBoardByIdCommand, Unit> {
    override val commandType = DeleteBoardByIdCommand::class

    @Transactional
    override fun handle(command: DeleteBoardByIdCommand) {
        boardService.deleteById(command.id)
    }
}

@Component
class AddBoardMemberHandler(
    private val boardService: BoardService,
    private val userService: UserService,
    private val boardMemberService: BoardMemberService
) : CommandHandler<AddBoardMemberCommand, BoardMember> {
    override val commandType = AddBoardMemberCommand::class

    @Transactional
    override fun handle(command: AddBoardMemberCommand): BoardMember {
        val id = BoardMember.Id(command.boardId, command.userId)
        val exists = boardMemberService.existsById(id)
        val board = boardService.findById(command.boardId)
        val user = userService.findById(command.userId)

        if (exists) {
            val member = boardMemberService.findById(id).apply {
                role = command.role
                startDate = command.startDate
                endDate = command.endDate
            }
            return boardMemberService.update(member)
        } else {
            val member = BoardMember(
                user = user,
                board = board,
                role = command.role,
                startDate = command.startDate,
                endDate = command.endDate,
            )
            return boardMemberService.create(member)
        }
    }
}

@Component
class RemoveBoardMemberHandler(
    private val boardService: BoardService
) : CommandHandler<RemoveBoardMemberCommand, Unit> {
    override val commandType = RemoveBoardMemberCommand::class

    @Transactional
    override fun handle(command: RemoveBoardMemberCommand) {
        val board = boardService.findById(command.boardId)

        // Check if member exists before removing
        val memberExists = board.members.any { it.userId == command.userId }
        if (!memberExists) {
            throw BoardMemberNotFoundException(command.boardId, command.userId)
        }

        boardService.removeMember(board, command.userId)
    }
}
