package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TargetCatalog(
    private val strategies: TargetStrategies,
    private val cohorts: CohortRepository,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun search(system: TargetSystem, query: String?): List<ExternalTarget> {
        val strategy = strategies.require(system)
        if (!strategy.descriptor.supports(TargetCapability.CATALOG)) return emptyList()

        val linked = linkedCohorts(system)
        return strategy.catalog(query).map { target ->
            target.copy(linkedCohortId = linked[target.externalId])
        }
    }

    fun descriptors(): List<TargetDescriptor> = strategies.descriptors()

    private fun linkedCohorts(system: TargetSystem): Map<String, Long> =
        cohorts.findAllBySystem(system.name)
            .mapNotNull { cohort -> cohort.externalId?.takeIf { it.isNotBlank() }?.let { it to cohort.id!! } }
            .toMap()
}
