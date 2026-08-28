package net.blueshell.api.board.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface BoardRepository : BaseRepository<Board, Long> {
    fun findByName(name: String): Optional<Board>

    @Query("SELECT b FROM Board b WHERE b.startDate <= :date AND (b.endDate IS NULL OR b.endDate >= :date)")
    fun findActiveBoard(date: LocalDate): Optional<Board>

    @Query("SELECT b FROM Board b ORDER BY b.startDate DESC")
    override fun findAll(): MutableList<Board>
}
