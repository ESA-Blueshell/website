package net.blueshell.api.platform.integration.cohort.port.`in`

import net.blueshell.api.platform.integration.cohort.application.DriftReport
import net.blueshell.api.shared.enums.TargetSystem

/**
 * Inbound port: computes the difference between what the external
 * system currently holds for a mapping and what the local engine
 * considers the desired membership.
 */
interface CohortDrift {
    fun compute(subjectId: Long, system: TargetSystem): DriftReport
}
