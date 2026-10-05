package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Schema(name = "FolderState", description = "A folder on the system and how many targets it holds.")
data class FolderState(
    val id: String,
    val name: String,
    val targets: Int,
)

@Schema(name = "FolderMerge", description = "What merging the folders that share a name did.")
data class FolderMerge(
    @field:Schema(description = "How many folders were removed.")
    val removed: Int,
    @field:Schema(description = "How many targets moved into the folder that stayed.")
    val moved: Int,
)

/** A system's folders as things of their own: what each holds, and the two ways one goes. */
interface FolderKeeper {
    /** Every folder with how many targets it holds. Two folders of one name are two entries. */
    fun states(): List<FolderState>

    /** Where folders share a name, files everything under the oldest and removes the others once they hold nothing. */
    fun merge(): FolderMerge

    /** Removes a folder that holds nothing; one that holds a target is refused. */
    fun remove(id: String)
}

/** The folders of a system the board tidies by hand: the ones that share a name and the ones holding nothing. */
@Service
class TargetFolders(
    private val strategies: TargetStrategies,
) {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun states(system: TargetSystem): List<FolderState> = refusedBy(system) { keeper(system)?.states().orEmpty() }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun merge(system: TargetSystem): FolderMerge = refusedBy(system) { require(system).merge() }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun remove(
        system: TargetSystem,
        id: String,
    ) = refusedBy(system) { require(system).remove(id) }

    private fun keeper(system: TargetSystem): FolderKeeper? = strategies.require(system).folderKeeper

    private fun require(system: TargetSystem): FolderKeeper =
        keeper(system) ?: throw TargetSystemRefused(system, "${system.shownName} has no folders")
}
