package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.board.api.BoardMemberService

/**
 * Board operations that touch more than one collaborator. Reads go straight to
 * [BoardService], whose base class already carries the transaction.
 */
@Service
class BoardUseCases(
    private val boardService: BoardService,
    private val boardMemberService: BoardMemberService,
    private val pictures: StoredPictures,
    private val userService: UserService,
) {
    @Transactional
    fun create(
        number: Int,
        name: String?,
        candidate: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        photo: String?,
        cheer: String? = null,
        accent: String? = null,
        description: String? = null,
        image: String? = null,
    ): Board {
        if (boardService.findByNumber(number) != null) throw DuplicateBoardException(number)
        val recorded = name?.ifBlank { null }
        val board = Board(
            number = number,
            candidate = candidateFor(candidate, recorded, number),
            startDate = startDate,
            name = recorded,
            endDate = endDate,
            cheer = cheer?.ifBlank { null },
            accent = accent?.ifBlank { null },
            description = description?.ifBlank { null },
            image = image,
        )
        board.replacePicture(pictures.of(photo, FileType.BOARD_PHOTO))
        return boardService.create(board)
    }

    // `version` is deliberately absent: the command carried one and the handler
    // never applied it, so board update has never used optimistic locking.
    @Transactional
    fun update(
        id: Long,
        number: Int,
        name: String?,
        candidate: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        photo: String?,
        cheer: String? = null,
        accent: String? = null,
        description: String? = null,
        image: String? = null,
    ): Board {
        val board = boardService.findById(id)
        val holder = boardService.findByNumber(number)
        if (holder != null && holder.id != id) throw DuplicateBoardException(number)
        val recorded = name?.ifBlank { null }
        board.number = number
        board.name = recorded
        board.candidate = candidateFor(candidate, recorded, number)
        board.startDate = startDate
        board.endDate = endDate
        board.cheer = cheer?.ifBlank { null }
        board.accent = accent?.ifBlank { null }
        board.description = description?.ifBlank { null }
        board.image = image
        board.replacePicture(pictures.of(photo, FileType.BOARD_PHOTO))
        return boardService.update(board)
    }

    /**
     * What goes into `candidate`, which is `NOT NULL` and which nothing reads.
     *
     * The column duplicates the name and is kept by decision, so a write that carries no
     * candidate of its own fills it with the board's name — or with its number, since a board
     * is free to have no name at all.
     */
    private fun candidateFor(candidate: String?, name: String?, number: Int): String =
        candidate?.ifBlank { null } ?: name ?: "Board $number"

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
        nickname: String? = null,
        description: String? = null,
        image: String? = null,
        portrait: String? = null,
    ): BoardMember {
        val board = boardService.findById(boardId)
        val user = userId?.let { userService.findById(it) }
        val existing = userId?.let { boardMemberService.findByBoardAndUser(boardId, it) }

        if (existing != null) {
            existing.role = role
            existing.startDate = startDate
            existing.endDate = endDate
            existing.displayName = displayName
            existing.nickname = nickname
            existing.description = description
            existing.image = image
            existing.replacePicture(pictures.of(portrait, FileType.BOARD_PORTRAIT))
            return boardMemberService.update(existing)
        }

        val seat = BoardMember(
            board = board,
            user = user,
            role = role,
            startDate = startDate,
            endDate = endDate,
            displayName = displayName,
            nickname = nickname,
            description = description,
            image = image,
        )
        seat.replacePicture(pictures.of(portrait, FileType.BOARD_PORTRAIT))
        return boardMemberService.create(seat)
    }

    @Transactional
    fun updateMember(
        id: Long,
        role: String,
        startDate: LocalDate,
        endDate: LocalDate?,
        displayName: String? = null,
        nickname: String? = null,
        description: String? = null,
        image: String? = null,
        portrait: String? = null,
    ): BoardMember {
        val seat = boardMemberService.findSeat(id)
        seat.role = role
        seat.startDate = startDate
        seat.endDate = endDate
        seat.displayName = displayName
        seat.nickname = nickname
        seat.description = description
        seat.image = image
        seat.replacePicture(pictures.of(portrait, FileType.BOARD_PORTRAIT))
        return boardMemberService.update(seat)
    }

    /** A null member detaches the seat, which keeps standing under its own name. */
    @Transactional
    fun linkMember(id: Long, userId: Long?): BoardMember {
        val seat = boardMemberService.findSeat(id)
        seat.user = userId?.let { userService.findById(it) }
        return boardMemberService.update(seat)
    }

    /**
     * Removes a board, and refuses one that still has seats.
     *
     * A board cascades every write to its seats, so a plain delete soft-deletes a whole year of
     * people along with it. Refused with the count, so a caller is told what is in the way and
     * a board added by mistake still goes in one gesture.
     */
    @Transactional
    fun remove(id: Long) {
        val board = boardService.findById(id)
        val seats = boardMemberService.seatsOn(id)
        if (seats > 0) throw BoardHoldsSeats(board.number, seats)
        boardService.deleteById(id)
    }

    @Transactional
    fun removeMember(id: Long) {
        // Looked up first so a seat that is not there answers 404 rather than silently nothing.
        boardMemberService.findSeat(id)
        boardMemberService.deleteById(id)
    }
}
