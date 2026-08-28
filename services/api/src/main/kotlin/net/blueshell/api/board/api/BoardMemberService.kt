package net.blueshell.api.board.api

import net.blueshell.api.board.domain.BoardMemberNotFoundException
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class BoardMemberService(
    repository: BoardMemberRepository
) : BaseModelService<BoardMember, Long, BoardMemberRepository>(repository) {
    @Transactional(readOnly = true)
    fun findSeat(id: Long): BoardMember =
        repository.findById(id).orElseThrow { BoardMemberNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByBoardAndUser(boardId: Long, userId: Long): BoardMember? =
        repository.findByBoardIdAndUserId(boardId, userId).orElse(null)

    @Transactional(readOnly = true)
    fun findByBoard(boardId: Long): List<BoardMember> = repository.findByBoardId(boardId)

    /**
     * Whether a member held a board seat overlapping the window. The board year is the unit
     * the association thinks in, so this is what "was on the board that year" reduces to.
     */
    @Transactional(readOnly = true)
    fun servedBetween(userId: Long, from: LocalDate, to: LocalDate): Boolean =
        repository.existsForUserInWindow(userId, from, to)

    @Transactional(readOnly = true)
    fun serversBetween(from: LocalDate, to: LocalDate): Set<Long> =
        repository.findUserIdsInWindow(from, to).toSet()
}
