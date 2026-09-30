package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.cohort.domain.BulkTargetMoveResult
import net.blueshell.api.cohort.domain.ExternalTarget
import net.blueshell.api.cohort.domain.FolderTidy
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetDescriptor
import net.blueshell.api.cohort.domain.TidyPlan
import net.blueshell.api.security.AdminOnly
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/management/cohort-targets")
@Tag(name = "Cohort Targets", description = "Admin: external cohort target catalog")
@AdminOnly
class CohortTargetController(
    private val catalog: TargetCatalog,
    private val tidy: FolderTidy,
) {
    @GetMapping("/systems")
    @Operation(operationId = "listCohortTargetSystems")
    fun systems(): List<TargetDescriptor> = catalog.descriptors()

    @GetMapping("/{system}")
    @Operation(operationId = "searchCohortTargets")
    fun targets(
        @PathVariable system: TargetSystem,
        @RequestParam(required = false) query: String?,
    ): List<ExternalTarget> = catalog.search(system, query)

    @GetMapping("/{system}/folders")
    @Operation(operationId = "listCohortTargetFolders")
    fun folders(
        @PathVariable system: TargetSystem,
    ): List<String> = catalog.folders(system)

    @PutMapping("/{system}/{externalId}/folder")
    @Operation(operationId = "moveCohortTarget")
    fun move(
        @PathVariable system: TargetSystem,
        @PathVariable externalId: String,
        @Valid @RequestBody request: MoveTargetRequest,
    ): ExternalTarget = catalog.move(system, externalId, request.folder)

    @PostMapping("/{system}")
    @Operation(operationId = "createExternalTarget")
    fun create(
        @PathVariable system: TargetSystem,
        @Valid @RequestBody request: CreateExternalTargetRequest,
    ): ExternalTarget = catalog.create(system, request.name.trim(), request.folder?.trim())

    @PutMapping("/{system}/{externalId}/name")
    @Operation(operationId = "renameExternalTarget")
    fun rename(
        @PathVariable system: TargetSystem,
        @PathVariable externalId: String,
        @Valid @RequestBody request: RenameExternalTargetRequest,
    ): ExternalTarget = catalog.rename(system, externalId, request.name.trim())

    @PostMapping("/{system}/{externalId}/archive")
    @Operation(operationId = "archiveExternalTarget")
    fun archive(
        @PathVariable system: TargetSystem,
        @PathVariable externalId: String,
    ): ExternalTarget = catalog.archive(system, externalId)

    // A POST with the typed name rather than a DELETE: the confirm travels in the body.
    @PostMapping("/{system}/{externalId}/delete")
    @Operation(operationId = "deleteExternalTarget")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable system: TargetSystem,
        @PathVariable externalId: String,
        @Valid @RequestBody request: DeleteExternalTargetRequest,
    ) = catalog.delete(system, externalId, request.name)

    @GetMapping("/{system}/tidy")
    @Operation(operationId = "previewFolderTidy")
    fun previewTidy(
        @PathVariable system: TargetSystem,
    ): TidyPlan = tidy.preview(system)

    @PostMapping("/{system}/tidy")
    @Operation(operationId = "applyFolderTidy")
    fun applyTidy(
        @PathVariable system: TargetSystem,
        @Valid @RequestBody request: ApplyTidyRequest,
    ): BulkTargetMoveResult = tidy.apply(system, request.externalIds)

    @PostMapping("/{system}/folders")
    @Operation(operationId = "createTargetFolder")
    fun createFolder(
        @PathVariable system: TargetSystem,
        @Valid @RequestBody request: CreateTargetFolderRequest,
    ): List<String> = catalog.createFolder(system, request.name.trim())

    @PutMapping("/{system}/folder")
    @Operation(operationId = "moveCohortTargets")
    fun moveAll(
        @PathVariable system: TargetSystem,
        @Valid @RequestBody request: BulkMoveTargetsRequest,
    ): BulkTargetMoveResult = catalog.moveAll(system, request.externalIds, request.folder)
}
