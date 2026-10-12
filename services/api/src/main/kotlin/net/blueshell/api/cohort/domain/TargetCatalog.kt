package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TargetCatalog(
    private val strategies: TargetStrategies,
    private val targets: TargetRepository,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun search(
        system: TargetSystem,
        query: String?,
    ): List<ExternalTarget> {
        val strategy = strategies.require(system)
        val linked = linkedTargets(system)
        return strategy.catalog(query).map { external ->
            external.copy(linkedTargetId = linked[external.externalId])
        }
    }

    /** Every folder the system has, so a destination can be chosen rather than typed. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun folders(system: TargetSystem): List<String> = strategies.require(system).folders()

    /** File a target under another folder. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun move(
        system: TargetSystem,
        externalId: String,
        folder: String,
    ): ExternalTarget {
        val strategy = strategies.require(system)
        val external =
            strategy.resolve(externalId)
                ?: throw IllegalArgumentException("No target $externalId in $system")

        val moved = strategy.move(external, folder)
        return moved.copy(linkedTargetId = linkedTargets(system)[moved.externalId])
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
    fun moveAll(
        system: TargetSystem,
        externalIds: List<String>,
        folder: String,
    ): BulkTargetMoveResult {
        val strategy = strategies.require(system)
        val ids = externalIds.distinct()
        val resolved = ids.associateWith { strategy.resolve(it) }
        val missing = ids.filter { resolved[it] == null }
        val known = strategy.folders()
        val destination = known.firstOrNull { it.equals(folder, ignoreCase = true) }

        val violations =
            buildList {
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

        val linked = linkedTargets(system)
        val moved = mutableListOf<ExternalTarget>()
        val failed = mutableListOf<FailedTargetMove>()
        for (id in ids) {
            val external = resolved.getValue(id)!!
            try {
                val result = strategy.move(external, destination!!)
                moved += result.copy(linkedTargetId = linked[result.externalId])
            } catch (ex: RuntimeException) {
                // The system refused this one. The moves already made stand, so the id is
                // reported rather than the whole call failing and hiding them.
                failed += FailedTargetMove(id, external.label, ex.message ?: "The system refused the move.")
            }
        }
        return BulkTargetMoveResult(moved = moved, failed = failed)
    }

    fun descriptors(): List<TargetDescriptor> = strategies.descriptors()

    private fun linkedTargets(system: TargetSystem): Map<String, Long> =
        targets
            .findAllBySystem(system.name)
            .mapNotNull { target -> target.externalId?.takeIf { it.isNotBlank() }?.let { it to target.id!! } }
            .toMap()
}
