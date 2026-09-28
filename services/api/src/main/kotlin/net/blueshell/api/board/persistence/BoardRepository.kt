package net.blueshell.api.board.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface BoardRepository : BaseRepository<Board, Long> {
    fun findByName(name: String): Optional<Board>

    /** The board holding this number, among the boards that exist. */
    fun findByNumber(number: Int): Optional<Board>

    /**
     * The board this stored picture belongs to, or nobody.
     *
     * There is at most one: `uk_boards_picture_deleted_at` says a picture backs one board.
     */
    @Query("SELECT b FROM Board b WHERE b.picture.id = :pictureId")
    fun findByPictureId(
        @Param("pictureId") pictureId: Long,
    ): Optional<Board>

    @Query("SELECT b FROM Board b WHERE b.startDate <= :date AND (b.endDate IS NULL OR b.endDate >= :date)")
    fun findActiveBoard(date: LocalDate): Optional<Board>

    /** How many boards ever held this number, deleted ones included, which the seed never writes again. */
    @Query(value = "SELECT COUNT(*) FROM boards WHERE number = :number", nativeQuery = true)
    fun countEverNumbered(
        @Param("number") number: Int,
    ): Long

    @Query("SELECT b FROM Board b ORDER BY b.startDate DESC")
    override fun findAll(): MutableList<Board>
}
