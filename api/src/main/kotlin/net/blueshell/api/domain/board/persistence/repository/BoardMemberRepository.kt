package net.blueshell.api.domain.board.persistence.repository

import net.blueshell.api.domain.board.persistence.BoardMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface BoardMemberRepository : JpaRepository<BoardMember, BoardMember.Id> {
    fun findByBoardIdAndUserId(boardId: Long, userId: Long): Optional<BoardMember>
    fun findByBoardId(boardId: Long): List<BoardMember>
}
