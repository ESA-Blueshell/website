package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
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
    fun findById(id: Long): Season = seasons.findById(id).orElseThrow { SeasonNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(name: String): Season? = seasons.findByNameIgnoreCase(name)

    /**
     * The season a date falls in, or the one before it when the date sits in a gap. A read
     * asked for "now" between two seasons should answer with the last roster that played, not
     * nothing.
     */
    @Transactional(readOnly = true)
    fun findCurrent(on: LocalDate = LocalDate.now()): Season? = seasons.findCurrentOn(on)

    @Transactional(readOnly = true)
    fun findAllOverlapping(
        from: LocalDate,
        to: LocalDate,
    ): List<Season> = seasons.findAllOverlapping(from, to)

    @Transactional
    fun create(input: SeasonInput): Season {
        requireOrdered(input.startDate, input.endDate)
        requireClear(input.startDate, input.endDate, itself = null)
        return seasons.save(Season(name = input.name.trim(), startDate = input.startDate, endDate = input.endDate))
    }

    @Transactional
    fun update(
        id: Long,
        input: SeasonInput,
    ): Season {
        requireOrdered(input.startDate, input.endDate)
        requireClear(input.startDate, input.endDate, itself = id)
        val season = findById(id)
        season.name = input.name.trim()
        season.startDate = input.startDate
        season.endDate = input.endDate
        return seasons.save(season)
    }

    @Transactional
    fun delete(id: Long) = seasons.delete(findById(id))

    private fun requireOrdered(
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        if (endDate.isBefore(startDate)) throw SeasonEndsBeforeStart()
    }

    /** A season may cover the same ground as itself, and as nothing else. */
    private fun requireClear(
        startDate: LocalDate,
        endDate: LocalDate,
        itself: Long?,
    ) {
        val clash = seasons.findAllOverlapping(startDate, endDate).firstOrNull { it.id != itself }
        if (clash != null) throw SeasonDatesOverlap(clash.name)
    }
}
