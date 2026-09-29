package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetDeletion
import net.blueshell.api.cohort.persistence.TargetDeletionRepository
import net.blueshell.api.contact.api.ContactServiceException
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.tracking.ActorProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TargetCatalog(
    private val strategies: TargetStrategies,
    private val cohorts: CohortRepository,
    private val deletions: TargetDeletionRepository,
    private val actors: ActorProvider,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun search(
        system: TargetSystem,
        query: String?,
    ): List<ExternalTarget> {
        val strategy = strategies.require(system)
        val linked = linkedCohorts(system)
        return strategy.catalog(query).map { target ->
            target.copy(linkedCohortId = linked[target.externalId])
        }
    }

    /**
     * Where a target sits on its system right now, read from the system itself: a moved target
     * shows its new folder. When the system cannot be reached, or no longer has the target, the
     * folder is unknown rather than guessed.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun placeOf(
        system: TargetSystem,
        externalId: String,
    ): TargetPlace {
        val found =
            runCatching { strategies.require(system).resolve(externalId) }
                .onFailure { log.warn("[cohort] could not read where {} target {} is: {}", system, externalId, it.message) }
                .getOrNull()
        return found?.let { TargetPlace(it.path, folderKnown = true) } ?: TargetPlace(listOf(system.shownName), folderKnown = false)
    }

    /** Make a target linked to no cohort, in [folder] or at the top level. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun create(
        system: TargetSystem,
        name: String,
        folder: String?,
    ): ExternalTarget = refusedBy(system) { strategies.require(system).create(name, folder?.takeIf { it.isNotBlank() }) }

    /** Give a target, linked or not, another name on its system. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun rename(
        system: TargetSystem,
        externalId: String,
        name: String,
    ): ExternalTarget {
        val strategy = strategies.require(system)
        val target = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        val renamed = refusedBy(system) { strategy.rename(target, name) }
        val linkedId = linkedCohorts(system)[renamed.externalId]
        // A linked cohort names its target by this label, so its page shows the new name.
        linkedId?.let { id ->
            cohorts.findById(id).ifPresent {
                it.label = name
                cohorts.save(it)
            }
        }
        return renamed.copy(linkedCohortId = linkedId)
    }

    /** Make a folder, or find the one already called [name]; answers every folder. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun createFolder(
        system: TargetSystem,
        name: String,
    ): List<String> = refusedBy(system) { strategies.require(system).createFolder(name) }

    /**
     * Move a target into the archive folder, made when it is missing. It keeps its contacts and
     * its link to a cohort, and is moved back like any other move.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun archive(
        system: TargetSystem,
        externalId: String,
    ): ExternalTarget {
        val strategy = strategies.require(system)
        val target = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        val archived =
            refusedBy(system) {
                strategy.createFolder(ARCHIVE_FOLDER)
                strategy.move(target, ARCHIVE_FOLDER)
            }
        return archived.copy(linkedCohortId = linkedCohorts(system)[archived.externalId])
    }

    /**
     * Delete a target for good. Only one linked to no cohort, only when [typedName] is its name
     * exactly, and every delete is recorded with who made it.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun delete(
        system: TargetSystem,
        externalId: String,
        typedName: String,
    ) {
        val strategy = strategies.require(system)
        val target = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        if (linkedCohorts(system).containsKey(externalId)) throw TargetStillLinked(system, externalId)
        if (typedName != target.label) throw TargetNameMismatch(typedName)
        refusedBy(system) { strategy.delete(target) }
        deletions.save(
            TargetDeletion(system.name, externalId, target.label, actors.currentOrSystem().userId, Instant.now()),
        )
        log.info("[cohort] deleted {} target {} '{}'", system, externalId, target.label)
    }

    // The system's own reason is what the board needs to see; nothing on our side changed.
    private fun <T> refusedBy(
        system: TargetSystem,
        call: () -> T,
    ): T =
        try {
            call()
        } catch (e: ContactServiceException) {
            throw TargetSystemRefused(system, e.message ?: "no reason given").apply { initCause(e) }
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
        val target =
            strategy.resolve(externalId)
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
        cohorts
            .findAllBySystem(system.name)
            .mapNotNull { cohort -> cohort.externalId?.takeIf { it.isNotBlank() }?.let { it to cohort.id!! } }
            .toMap()

    companion object {
        /** Where archived lists go; Brevo cannot undo a delete, so archiving is the everyday action. */
        const val ARCHIVE_FOLDER = "Archive"
        private val log = LoggerFactory.getLogger(TargetCatalog::class.java)
    }
}

/** A target's path on its system, outside in, and whether its folder could be read. */
data class TargetPlace(
    val path: List<String>,
    val folderKnown: Boolean,
)
