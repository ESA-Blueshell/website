package net.blueshell.api.platform.integration.cohort.port.out

import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.stereotype.Component

/**
 * Central registry of all registered [CohortPort] implementations.
 * Replaces ad-hoc `ports.single { it.system == system }` lookups.
 * A missing port becomes a [NonRetryableJobException] (terminal FAILED)
 * rather than an infinite retry.
 */
@Component
class CohortPortRegistry(ports: List<CohortPort>) {
    private val bySystem: Map<TargetSystem, CohortPort> = ports.associateBy { it.system }

    fun require(system: TargetSystem): CohortPort =
        bySystem[system]
            ?: throw NonRetryableJobException("No CohortPort registered for $system")

    fun systems(): Set<TargetSystem> = bySystem.keys
}
