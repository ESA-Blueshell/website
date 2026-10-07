package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType.ACTIVISTS
import net.blueshell.api.cohort.persistence.CohortType.BOARD
import net.blueshell.api.cohort.persistence.CohortType.BOARD_YEAR_MEMBERS
import net.blueshell.api.cohort.persistence.CohortType.CURRENT_MEMBERS
import net.blueshell.api.cohort.persistence.CohortType.KANDI
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Recomputes the cohorts read from today's date, just after midnight. No event marks a board taking
 * office or a membership running out, so without this the board would change hands, and Kandi
 * empty, only when somebody next edited a board. A board's own year begins that day too, so its
 * cohort is registered first and exists from the board's first day.
 */
@Component
class DatedCohortSweep(
    private val definitions: CohortDefinitionRegistry,
    private val updater: CohortMembershipUpdater,
    private val registrar: CohortRegistrar,
) {
    @Scheduled(cron = $$"${cohort.dated-cron:0 5 0 * * *}", zone = "Europe/Amsterdam")
    fun recompute() {
        runCatching { registrar.register() }.onFailure { log.warn("[cohort] could not register today's cohorts: {}", it.message) }
        definitions
            .all()
            .filter { it.type in DATED }
            .forEach { definition ->
                runCatching { updater.updateCohort(definition) }
                    .onFailure { log.warn("[cohort] could not recompute {}: {}", definition.key, it.message) }
            }
    }

    private companion object {
        val DATED = setOf(BOARD, KANDI, ACTIVISTS, CURRENT_MEMBERS, BOARD_YEAR_MEMBERS)
        val log = LoggerFactory.getLogger(DatedCohortSweep::class.java)
    }
}
