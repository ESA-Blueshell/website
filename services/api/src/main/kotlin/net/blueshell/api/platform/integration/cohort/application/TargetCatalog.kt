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

    /** Every folder the system has, so a destination can be chosen rather than typed. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun folders(system: TargetSystem): List<String> = strategies.require(system).folders()

    /**
     * File a target under another folder.
     *
     * A system that cannot move one says so through its capabilities, and asking anyway is a
     * bad request rather than a fault.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun move(system: TargetSystem, externalId: String, folder: String): ExternalTarget {
        val strategy = strategies.require(system)
        require(strategy.descriptor.supports(TargetCapability.MOVE)) {
            "$system cannot move a target between folders"
        }
        val target = strategy.resolve(externalId)
            ?: throw IllegalArgumentException("No target $externalId in $system")

        val moved = strategy.move(target, folder)
        return moved.copy(linkedCohortId = linkedCohorts(system)[moved.externalId])
    }

    fun descriptors(): List<TargetDescriptor> = strategies.descriptors()

    private fun linkedCohorts(system: TargetSystem): Map<String, Long> =
        cohorts.findAllBySystem(system.name)
            .mapNotNull { cohort -> cohort.externalId?.takeIf { it.isNotBlank() }?.let { it to cohort.id!! } }
            .toMap()
}
