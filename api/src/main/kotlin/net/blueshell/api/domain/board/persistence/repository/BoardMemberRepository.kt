package net.blueshell.api.domain.board.persistence.repository

import net.blueshell.api.domain.board.persistence.BoardMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BoardMemberRepository : JpaRepository<BoardMember, BoardMember.Id> {
    @Query("SELECT bm FROM BoardMember bm WHERE bm.id.boardId = :boardId AND bm.id.userId = :userId")
    fun findByBoardIdAndUserId(boardId: Long, userId: Long): Optional<BoardMember>

    @Query("SELECT bm FROM BoardMember bm WHERE bm.id.boardId = :boardId")
    fun findByBoardId(boardId: Long): List<BoardMember>
}
