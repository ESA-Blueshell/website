package net.blueshell.api.domain.esports.persistence.repository

import net.blueshell.api.domain.esports.persistence.Season
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    fun findByNameIgnoreCase(name: String): Season?

    fun findAllByOrderByStartDateDesc(): List<Season>

    /** Every season that ran during the window, which is how a period asks about play. */
    @Query("SELECT s FROM Season s WHERE s.startDate <= :to AND s.endDate >= :from")
    fun findAllOverlapping(@Param("from") from: LocalDate, @Param("to") to: LocalDate): List<Season>

    /** The season covering a date, or the most recent one behind it when between seasons. */
    @Query(
        """
        SELECT s FROM Season s
        WHERE s.startDate <= :on
        ORDER BY s.startDate DESC
        LIMIT 1
        """,
    )
    fun findCurrentOn(@Param("on") on: LocalDate): Season?
}
