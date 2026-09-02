package net.blueshell.api.board.persistence

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

    /** How many members a board still has, which is what stands in the way of removing it. */
    @Query("SELECT COUNT(bm) FROM BoardMember bm WHERE bm.board.id = :boardId")
    fun countByBoardId(@Param("boardId") boardId: Long): Long

    /**
     * The board member this stored picture belongs to, or nobody.
     *
     * There is at most one: `uk_board_members_picture_deleted_at` says a portrait backs one member.
     */
    @Query("SELECT bm FROM BoardMember bm WHERE bm.picture.id = :pictureId")
    fun findByPictureId(@Param("pictureId") pictureId: Long): Optional<BoardMember>

    /**
     * Whether an account held a place on a board overlapping the window. A board runs from its
     * own start to its own end, so the membership's dates are what the question asks about.
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

    /** Everybody who held a place on a board overlapping the window, ignoring unlinked ones. */
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
