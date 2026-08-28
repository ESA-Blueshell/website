package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BoardService @Autowired constructor(
    repository: BoardRepository
) : BaseModelService<Board, Long, BoardRepository>(repository) {

    @Transactional(readOnly = true)
    override fun findById(id: Long): Board {
        return repository.findById(id)
            .orElseThrow { BoardNotFoundException(id) }
    }

    @Transactional
    fun addMember(board: Board, member: BoardMember) {
        board.addMember(member)
        repository.save(board)
    }

    @Transactional
    fun removeMember(board: Board, userId: Long) {
        board.removeMember(userId)
        repository.save(board)
    }
}
