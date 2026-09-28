package net.blueshell.api.board.domain

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Every board read and write, straight against the repository. */
@Service
class BoardUseCases(
    private val boards: BoardRepository,
    private val boardMemberService: BoardMemberService,
    private val pictures: StoredPictures,
    private val userService: UserService,
) {
    /** Every board, newest first. */
    @Transactional(readOnly = true)
    fun all(): List<Board> = boards.findAll()

    @Transactional(readOnly = true)
    fun byId(id: Long): Board = boards.findById(id).orElseThrow { BoardNotFound(id) }

    @Transactional
    fun create(input: BoardInput): Board {
        if (boards.findByNumber(input.number).isPresent) throw DuplicateBoardException(input.number)
        return written(Board(number = input.number, candidate = "", startDate = input.startDate), input)
    }

    /** The edit is refused where [version] is not the one the board is at: somebody saved since. */
    @Transactional
    fun update(
        id: Long,
        input: BoardInput,
        version: Long?,
    ): Board {
        val board = byId(id)
        version?.let(board::requireVersion)
        if (boards.findByNumber(input.number).filter { it.id != id }.isPresent) throw DuplicateBoardException(input.number)
        return written(board, input)
    }

    private fun written(
        board: Board,
        input: BoardInput,
    ): Board {
        val recorded = input.name?.ifBlank { null }
        board.number = input.number
        board.name = recorded
        board.candidate = candidateFor(input.candidate, recorded, input.number)
        board.startDate = input.startDate
        board.endDate = input.endDate
        board.cheer = input.cheer?.ifBlank { null }
        board.accent = input.accent?.ifBlank { null }
        board.description = input.description?.ifBlank { null }
        board.replacePicture(pictures.of(input.photo, FileType.BOARD_PHOTO))
        return boards.saveAndFlush(board)
    }

    /**
     * What goes into `candidate`, which is `NOT NULL` and which nothing reads.
     *
     * The column duplicates the name and is kept by decision, so a write that carries no
     * candidate of its own fills it with the board's name — or with its number, since a board
     * is free to have no name at all.
     */
    private fun candidateFor(
        candidate: String?,
        name: String?,
        number: Int,
    ): String = candidate?.ifBlank { null } ?: name ?: "Board $number"

    /**
     * Puts somebody on a board. [userId] is absent for the people most of the history is
     * made of, who never had an account here: their membership stands under the display name.
     *
     * An account already on this board keeps its membership and has it updated; a membership
     * with no account is always a new one, since there is nothing to match it on.
     */
    @Transactional
    fun addMember(
        boardId: Long,
        userId: Long?,
        input: BoardMemberInput,
    ): BoardMember {
        val board = byId(boardId)
        val user = userId?.let { userService.findById(it) }
        val existing = userId?.let { boardMemberService.findByBoardAndUser(boardId, it) }
        if (existing != null) return boardMemberService.update(written(existing, input))
        val member = BoardMember(board = board, user = user, role = input.role, startDate = input.startDate)
        return boardMemberService.create(written(member, input))
    }

    @Transactional
    fun updateMember(
        id: Long,
        input: BoardMemberInput,
    ): BoardMember = boardMemberService.update(written(boardMemberService.findMember(id), input))

    private fun written(
        member: BoardMember,
        input: BoardMemberInput,
    ): BoardMember {
        member.role = input.role
        member.startDate = input.startDate
        member.endDate = input.endDate
        member.displayName = input.displayName
        member.nickname = input.nickname
        member.description = input.description
        member.replacePicture(pictures.of(input.portrait, FileType.BOARD_PORTRAIT))
        return member
    }

    /** A null account detaches the membership, which keeps standing under its own name. */
    @Transactional
    fun linkMember(
        id: Long,
        userId: Long?,
    ): BoardMember {
        val member = boardMemberService.findMember(id)
        member.user = userId?.let { userService.findById(it) }
        return boardMemberService.update(member)
    }

    /**
     * Removes a board, and refuses one that still has members.
     *
     * A board cascades every write to its members, so a plain delete soft-deletes a whole year of
     * people along with it. Refused with the count, so a caller is told what is in the way and
     * a board added by mistake still goes in one gesture.
     */
    @Transactional
    fun remove(id: Long) {
        val board = byId(id)
        val members = boardMemberService.membersOn(id)
        if (members > 0) throw BoardHoldsMembers(board.number, members)
        boards.delete(board)
    }

    @Transactional
    fun removeMember(id: Long) {
        // Looked up first so a member that is not there answers 404 rather than silently nothing.
        boardMemberService.findMember(id)
        boardMemberService.deleteById(id)
    }
}
