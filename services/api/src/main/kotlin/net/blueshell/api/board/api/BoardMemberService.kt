package net.blueshell.api.board.api

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.board.domain.BoardMemberNotFoundException
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class BoardMemberService(
    private val repository: BoardMemberRepository,
    private val trackedEvents: TrackedEventPublisher,
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
    fun create(member: BoardMember): BoardMember = written(member).also(::changed)

    @Transactional
    fun update(member: BoardMember): BoardMember = rewritten(member).also(::changed)

    @Transactional
    fun deleteById(id: Long) {
        val member = findMember(id)
        repository.delete(member)
        changed(member)
    }

    // A place held by somebody with no account here changes nobody's cohorts.
    private fun changed(member: BoardMember) {
        val userId = member.user?.id ?: return
        trackedEvents.publish { actor -> BoardMembershipChanged(userId, actor) }
    }

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

    /** Everybody on a board that has not taken office by [day]: Kandi, until the board's first day. */
    @Transactional(readOnly = true)
    fun candidatesOn(day: LocalDate): Set<Long> = repository.findUserIdsOnBoardsStartingAfter(day).toSet()

    /** Everybody serving on [day] on a board that has taken office by then. */
    @Transactional(readOnly = true)
    fun servingOn(day: LocalDate): Set<Long> = repository.findUserIdsServingOn(day).toSet()
}
