package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.FolderMerge
import net.blueshell.api.cohort.domain.FolderState
import net.blueshell.api.cohort.domain.TargetFolders
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** A system's folders as the board tidies them: what each holds, merging the ones that share a name, removing an empty one. */
@RestController
@RequestMapping("/management/cohort-targets/{system}/folders")
@Tag(name = "Cohort Targets")
@BoardOnly
class TargetFolderController(
    private val folders: TargetFolders,
) {
    @GetMapping("/states")
    @Operation(operationId = "listTargetFolderStates")
    fun states(
        @PathVariable system: TargetSystem,
    ): List<FolderState> = folders.states(system)

    @PostMapping("/merge")
    @Operation(operationId = "mergeTargetFolders", summary = "Files everything under one of the folders sharing a name")
    fun merge(
        @PathVariable system: TargetSystem,
    ): FolderMerge = folders.merge(system)

    @DeleteMapping("/{folderId}")
    @Operation(operationId = "removeTargetFolder", summary = "Removes a folder that holds nothing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable system: TargetSystem,
        @PathVariable folderId: String,
    ) = folders.remove(system, folderId)
}
