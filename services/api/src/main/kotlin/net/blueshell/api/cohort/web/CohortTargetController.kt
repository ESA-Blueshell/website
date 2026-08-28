package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.ExternalTarget
import net.blueshell.api.cohort.domain.TargetDescriptor
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.security.access.prepost.PreAuthorize
import jakarta.validation.Valid
import net.blueshell.api.cohort.domain.BulkTargetMoveResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/management/cohort-targets")
@Tag(name = "Cohort Targets", description = "Admin: external cohort target catalog")
@PreAuthorize("hasAuthority('ADMIN')")
class CohortTargetController(
    private val catalog: TargetCatalog,
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
    fun folders(@PathVariable system: TargetSystem): List<String> = catalog.folders(system)

    @PutMapping("/{system}/{externalId}/folder")
    @Operation(operationId = "moveCohortTarget")
    fun move(
        @PathVariable system: TargetSystem,
        @PathVariable externalId: String,
        @Valid @RequestBody request: MoveTargetRequest,
    ): ExternalTarget = catalog.move(system, externalId, request.folder)

    @PutMapping("/{system}/folder")
    @Operation(operationId = "moveCohortTargets")
    fun moveAll(
        @PathVariable system: TargetSystem,
        @Valid @RequestBody request: BulkMoveTargetsRequest,
    ): BulkTargetMoveResult = catalog.moveAll(system, request.externalIds, request.folder)
}
