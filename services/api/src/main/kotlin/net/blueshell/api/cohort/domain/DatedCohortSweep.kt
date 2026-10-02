package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Recomputes the cohorts read from today's date, just after midnight. No event marks a board taking
 * office or a membership running out, so without this the board would change hands, and Kandi
 * empty, only when somebody next edited a board.
 */
@Component
class DatedCohortSweep(
    private val definitions: CohortDefinitionRegistry,
    private val updater: CohortMembershipUpdater,
) {
    @Scheduled(cron = $$"${cohort.dated-cron:0 5 0 * * *}", zone = "Europe/Amsterdam")
    fun recompute() {
        definitions
            .all()
            .filter { it.type in DATED }
            .forEach { definition ->
                runCatching { updater.updateCohort(definition) }
                    .onFailure { log.warn("[cohort] could not recompute {}: {}", definition.key, it.message) }
            }
    }

    private companion object {
        val DATED = setOf(CohortType.BOARD, CohortType.KANDI, CohortType.ACTIVISTS, CohortType.CURRENT_MEMBERS)
        val log = LoggerFactory.getLogger(DatedCohortSweep::class.java)
    }
}
