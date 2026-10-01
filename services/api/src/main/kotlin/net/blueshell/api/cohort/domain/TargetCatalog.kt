package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetDeletion
import net.blueshell.api.cohort.persistence.TargetDeletionRepository
import net.blueshell.api.cohort.persistence.TargetRepository
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
    private val targets: TargetRepository,
    private val deletions: TargetDeletionRepository,
    private val actors: ActorProvider,
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

    /** One target as its system has it now, with the cohort target it is linked to. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun find(
        system: TargetSystem,
        externalId: String,
    ): ExternalTarget {
        val found = strategies.require(system).resolve(externalId) ?: throw TargetNotFound(system, externalId)
        return found.copy(linkedTargetId = linkedTargets(system)[externalId])
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
        val external = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        val renamed = refusedBy(system) { strategy.rename(external, name) }
        val linkedId = linkedTargets(system)[renamed.externalId]
        // A linked cohort names its target by this label, so its page shows the new name.
        linkedId?.let { id ->
            targets.findById(id).ifPresent {
                it.label = name
                targets.save(it)
            }
        }
        return renamed.copy(linkedTargetId = linkedId)
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
        val external = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        val archived =
            refusedBy(system) {
                strategy.createFolder(ARCHIVE_FOLDER)
                strategy.move(external, ARCHIVE_FOLDER)
            }
        return archived.copy(linkedTargetId = linkedTargets(system)[archived.externalId])
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
        val external = strategy.resolve(externalId) ?: throw TargetNotFound(system, externalId)
        if (linkedTargets(system).containsKey(externalId)) throw TargetStillLinked(system, externalId)
        if (typedName != external.label) throw TargetNameMismatch(typedName)
        refusedBy(system) { strategy.delete(external) }
        deletions.save(
            TargetDeletion(system.name, externalId, external.label, actors.currentOrSystem().userId, Instant.now()),
        )
        log.info("[cohort] deleted {} target {} '{}'", system, externalId, external.label)
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
