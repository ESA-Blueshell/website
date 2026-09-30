package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * The one-off folder tidy: every list linked to a cohort that is not in its cohort type's folder is
 * proposed a move there. A proposal, not a rule: once applied, Brevo's folder is the truth again
 * and nothing moves a list back. Unlinked lists are never proposed.
 */
@Service
class FolderTidy(
    private val strategies: TargetStrategies,
    private val cohorts: CohortRepository,
    private val subjects: CohortSubjectRepository,
) {
    /** What the tidy would do; changes nothing. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun preview(system: TargetSystem): TidyPlan {
        val strategy = strategies.require(system)
        val catalog = strategy.catalog(null).associateBy { it.externalId }
        val typeOf = subjects.findAll().associate { it.id to it.type }
        val moves =
            cohorts
                .findAllBySystem(system.name)
                .mapNotNull { cohort ->
                    val target = cohort.externalId?.let(catalog::get) ?: return@mapNotNull null
                    val type = typeOf[cohort.subjectId] ?: return@mapNotNull null
                    val folder = CohortFolders.forType(type)
                    if (target.folderLabel.equals(folder, ignoreCase = true)) return@mapNotNull null
                    TidyMove(target.externalId, target.label, target.folderLabel, folder)
                }.sortedWith(compareBy({ it.to }, { it.label }))
        val known = strategy.folders().map { it.lowercase() }.toSet()
        val toCreate = moves.map { it.to }.distinct().filter { it.lowercase() !in known }
        return TidyPlan(moves, toCreate)
    }

    /** Moves the lists picked out of the preview, making their folders first; each failure keeps its reason. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun apply(
        system: TargetSystem,
        externalIds: Collection<String>,
    ): BulkTargetMoveResult {
        val strategy = strategies.require(system)
        val chosen = preview(system).moves.filter { it.externalId in externalIds }
        val unmade =
            chosen
                .map { it.to }
                .distinct()
                .mapNotNull { folder -> runCatching { strategy.createFolder(folder) }.exceptionOrNull()?.let { folder to it } }
                .toMap()
        val moved = mutableListOf<ExternalTarget>()
        val failed = mutableListOf<FailedTargetMove>()
        chosen.forEach { move ->
            val outcome =
                unmade[move.to]?.let { Result.failure(it) }
                    ?: runCatching {
                        val target = strategy.resolve(move.externalId) ?: error("${system.shownName} no longer has this list")
                        strategy.move(target, move.to)
                    }
            outcome
                .onSuccess { moved += it }
                .onFailure { failed += FailedTargetMove(move.externalId, move.label, it.message ?: "refused") }
        }
        return BulkTargetMoveResult(moved, failed)
    }
}

@Schema(name = "TidyPlan", description = "The folder tidy's proposal: the moves, and the folders it would make.")
data class TidyPlan(
    val moves: List<TidyMove>,
    @field:Schema(description = "Folders the moves need that the system does not have yet.")
    val foldersToCreate: List<String>,
)

@Schema(name = "TidyMove", description = "One linked list the tidy would move into its cohort type's folder.")
data class TidyMove(
    val externalId: String,
    val label: String,
    @field:Schema(description = "The folder it is in now; null at the top level.")
    val from: String?,
    val to: String,
)
