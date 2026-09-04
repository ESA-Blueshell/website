package net.blueshell.api.board.api

import net.blueshell.api.board.persistence.BoardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** How many boards have sat, for a reader outside this module. */
@Service
class BoardCounts(private val repository: BoardRepository) {

    @Transactional(readOnly = true)
    fun count(): Long = repository.count()
}
