package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
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

    /**
     * File several targets under one folder.
     *
     * The whole selection is checked first and refused with nothing sent if it fails, as the
     * bulk contribution actions do. Past that each move is one call to a system with no
     * transaction, so a later failure leaves earlier moves standing and the result names both
     * halves rather than picking one.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun moveAll(system: TargetSystem, externalIds: List<String>, folder: String): BulkTargetMoveResult {
        val strategy = strategies.require(system)
        require(strategy.descriptor.supports(TargetCapability.MOVE)) {
            "$system cannot move a target between folders"
        }

        val ids = externalIds.distinct()
        val resolved = ids.associateWith { strategy.resolve(it) }
        val missing = ids.filter { resolved[it] == null }
        val known = strategy.folders()
        val destination = known.firstOrNull { it.equals(folder, ignoreCase = true) }

        val violations = buildList {
            if (destination == null) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "folder",
                        code = BulkSelectionRejected.UNKNOWN_FOLDER,
                        message = "There is no folder called \"$folder\" in $system.",
                        refs = listOf(folder),
                    ),
                )
            }
            if (missing.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "externalIds",
                        code = BulkSelectionRejected.UNKNOWN_TARGETS,
                        message = "${missing.size} of the selected targets no longer exist in $system.",
                        refs = missing,
                    ),
                )
            }
        }
        if (violations.isNotEmpty()) throw BulkSelectionRejected("BulkMoveTargetsRequest", violations)

        val linked = linkedCohorts(system)
        val moved = mutableListOf<ExternalTarget>()
        val failed = mutableListOf<FailedTargetMove>()
        for (id in ids) {
            val target = resolved.getValue(id)!!
            try {
                val result = strategy.move(target, destination!!)
                moved += result.copy(linkedCohortId = linked[result.externalId])
            } catch (ex: RuntimeException) {
                // The system refused this one. The moves already made stand, so the id is
                // reported rather than the whole call failing and hiding them.
                failed += FailedTargetMove(id, target.label, ex.message ?: "The system refused the move.")
            }
        }
        return BulkTargetMoveResult(moved = moved, failed = failed)
    }

    fun descriptors(): List<TargetDescriptor> = strategies.descriptors()

    private fun linkedCohorts(system: TargetSystem): Map<String, Long> =
        cohorts.findAllBySystem(system.name)
            .mapNotNull { cohort -> cohort.externalId?.takeIf { it.isNotBlank() }?.let { it to cohort.id!! } }
            .toMap()
}
