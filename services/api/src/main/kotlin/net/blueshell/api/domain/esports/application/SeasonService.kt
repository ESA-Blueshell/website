package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.application.exception.SeasonNotFoundException
import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.domain.esports.persistence.repository.SeasonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class SeasonService(
    private val seasons: SeasonRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<Season> = seasons.findAllByOrderByStartDateDesc()

    @Transactional(readOnly = true)
    fun findById(id: Long): Season =
        seasons.findById(id).orElseThrow { SeasonNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(name: String): Season? = seasons.findByNameIgnoreCase(name)

    /**
     * The season a date falls in, or the one before it when the date sits in a gap. A page
     * asked for "now" between two seasons should show the last roster that played, not
     * nothing.
     */
    @Transactional(readOnly = true)
    fun findCurrent(on: LocalDate = LocalDate.now()): Season? = seasons.findCurrentOn(on)

    @Transactional(readOnly = true)
    fun findAllOverlapping(from: LocalDate, to: LocalDate): List<Season> =
        seasons.findAllOverlapping(from, to)

    @Transactional
    fun create(name: String, startDate: LocalDate, endDate: LocalDate): Season {
        requireOrdered(startDate, endDate)
        return seasons.save(Season(name = name.trim(), startDate = startDate, endDate = endDate))
    }

    @Transactional
    fun update(id: Long, name: String, startDate: LocalDate, endDate: LocalDate): Season {
        requireOrdered(startDate, endDate)
        val season = findById(id)
        season.name = name.trim()
        season.startDate = startDate
        season.endDate = endDate
        return seasons.save(season)
    }

    @Transactional
    fun delete(id: Long) = seasons.delete(findById(id))

    private fun requireOrdered(startDate: LocalDate, endDate: LocalDate) =
        require(!endDate.isBefore(startDate)) { "A season cannot end before it starts" }
}
