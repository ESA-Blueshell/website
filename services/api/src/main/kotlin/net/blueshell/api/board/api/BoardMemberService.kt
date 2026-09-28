package net.blueshell.api.board.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.board.domain.BoardMemberNotFoundException
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class BoardMemberService(
    private val repository: BoardMemberRepository,
) {
    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: BoardMember): BoardMember = repository.saveAndFlush(row).also(em::refresh)

    // The existence query flushes the session first, which writes what the edit cascades before
    // the merge; merging it unwritten fails on a lazy owner.
    private fun rewritten(row: BoardMember): BoardMember {
        val id = row.id
        if (id == null || !repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "BoardMember not found with id: $id")
        }
        return written(row)
    }

    @Transactional
    fun create(member: BoardMember): BoardMember = written(member)

    @Transactional
    fun update(member: BoardMember): BoardMember = rewritten(member)

    @Transactional
    fun deleteById(id: Long) = repository.delete(findMember(id))

    @Transactional(readOnly = true)
    fun findMember(id: Long): BoardMember = repository.findById(id).orElseThrow { BoardMemberNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByBoardAndUser(
        boardId: Long,
        userId: Long,
    ): BoardMember? = repository.findByBoardIdAndUserId(boardId, userId).orElse(null)

    @Transactional(readOnly = true)
    fun findByBoard(boardId: Long): List<BoardMember> = repository.findByBoardId(boardId)

    /** How many members a board still has. Counted rather than listed: only the number is asked for. */
    @Transactional(readOnly = true)
    fun membersOn(boardId: Long): Long = repository.countByBoardId(boardId)

    /**
     * Whether a member held a place on a board overlapping the window. The board year is the unit
     * the association thinks in, so this is what "was on the board that year" reduces to.
     */
    @Transactional(readOnly = true)
    fun servedBetween(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): Boolean = repository.existsForUserInWindow(userId, from, to)

    @Transactional(readOnly = true)
    fun serversBetween(
        from: LocalDate,
        to: LocalDate,
    ): Set<Long> = repository.findUserIdsInWindow(from, to).toSet()
}
