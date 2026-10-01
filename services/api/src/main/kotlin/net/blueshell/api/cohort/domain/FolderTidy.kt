package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.AppliedTidy
import net.blueshell.api.cohort.persistence.AppliedTidyRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The one-off folder tidy: every list linked to a cohort that is not in its cohort type's folder is
 * proposed a move there. A proposal, not a rule: once applied, Brevo's folder is the truth again
 * and nothing moves a list back. Unlinked lists are never proposed.
 */
@Service
class FolderTidy(
    private val strategies: TargetStrategies,
    private val targets: TargetRepository,
    private val cohorts: CohortRepository,
    private val applied: AppliedTidyRepository,
    private val actors: ActorProvider,
    private val users: UserService,
) {
    /** What the tidy would do; changes nothing. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun preview(system: TargetSystem): TidyPlan {
        val strategy = strategies.require(system)
        val catalog = strategy.catalog(null).associateBy { it.externalId }
        val typeOf = cohorts.findAll().associate { it.id to it.type }
        val moves =
            targets
                .findAllBySystem(system.name)
                .mapNotNull { target ->
                    val external = target.externalId?.let(catalog::get) ?: return@mapNotNull null
                    val type = typeOf[target.cohortId] ?: return@mapNotNull null
                    val folder = CohortFolders.forType(type)
                    if (external.folderLabel.equals(folder, ignoreCase = true)) return@mapNotNull null
                    TidyMove(external.externalId, external.label, external.folderLabel, folder)
                }.sortedWith(compareBy({ it.to }, { it.label }))
        val known = strategy.folders().map { it.lowercase() }.toSet()
        val toCreate = moves.map { it.to }.distinct().filter { it.lowercase() !in known }
        return TidyPlan(moves, toCreate, lastApplied(system))
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
                        val external = strategy.resolve(move.externalId) ?: error("${system.shownName} no longer has this list")
                        strategy.move(external, move.to)
                    }
            outcome
                .onSuccess { moved += it }
                .onFailure { failed += FailedTargetMove(move.externalId, move.label, it.message ?: "refused") }
        }
        applied.save(AppliedTidy(system.name, moved.size, failed.size, actors.currentOrSystem().userId, Instant.now()))
        return BulkTargetMoveResult(moved, failed)
    }

    private fun lastApplied(system: TargetSystem): LastTidy? =
        applied.findFirstBySystemOrderByAppliedAtDesc(system.name)?.let { tidy ->
            val by = tidy.appliedBy?.let { id -> users.findAllByIds(setOf(id)).firstOrNull()?.fullName }
            LastTidy(tidy.appliedAt, by, tidy.moved, tidy.failed)
        }
}

@Schema(name = "TidyPlan", description = "The folder tidy's proposal: the moves, and the folders it would make.")
data class TidyPlan(
    val moves: List<TidyMove>,
    @field:Schema(description = "Folders the moves need that the system does not have yet.")
    val foldersToCreate: List<String>,
    @field:Schema(description = "The newest applied tidy on the system, if any.")
    val lastApplied: LastTidy? = null,
)

@Schema(name = "LastTidy", description = "When a tidy was applied, by whom, and what it moved.")
data class LastTidy(
    val appliedAt: Instant,
    @field:Schema(description = "Who applied it; null when the api did on its own behalf.")
    val appliedByName: String?,
    val moved: Int,
    val failed: Int,
)

@Schema(name = "TidyMove", description = "One linked list the tidy would move into its cohort type's folder.")
data class TidyMove(
    val externalId: String,
    val label: String,
    @field:Schema(description = "The folder it is in now; null at the top level.")
    val from: String?,
    val to: String,
)
