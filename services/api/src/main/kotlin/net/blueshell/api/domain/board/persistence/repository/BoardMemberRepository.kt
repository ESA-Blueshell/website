package net.blueshell.api.domain.board.persistence.repository

import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface BoardMemberRepository : BaseRepository<BoardMember, Long> {
    @Query("SELECT bm FROM BoardMember bm WHERE bm.board.id = :boardId AND bm.user.id = :userId")
    fun findByBoardIdAndUserId(
        @Param("boardId") boardId: Long,
        @Param("userId") userId: Long,
    ): Optional<BoardMember>

    @Query("SELECT bm FROM BoardMember bm WHERE bm.board.id = :boardId")
    fun findByBoardId(@Param("boardId") boardId: Long): List<BoardMember>

    /**
     * Whether a member held a board seat overlapping the window — a board runs from its own
     * start to its own end, so the seat's dates are what the question asks about.
     */
    @Query(
        """
        SELECT COUNT(bm) > 0 FROM BoardMember bm
        WHERE bm.user.id = :userId
          AND bm.startDate <= :to
          AND (bm.endDate IS NULL OR bm.endDate >= :from)
        """,
    )
    fun existsForUserInWindow(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): Boolean

    /** Everybody who held a board seat overlapping the window, ignoring unlinked seats. */
    @Query(
        """
        SELECT DISTINCT bm.user.id FROM BoardMember bm
        WHERE bm.user IS NOT NULL
          AND bm.startDate <= :to
          AND (bm.endDate IS NULL OR bm.endDate >= :from)
        """,
    )
    fun findUserIdsInWindow(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<Long>
}
