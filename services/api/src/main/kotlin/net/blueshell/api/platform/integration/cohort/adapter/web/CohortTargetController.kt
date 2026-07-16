package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.TargetCatalog
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
import net.blueshell.api.platform.integration.cohort.port.out.TargetCapability
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
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
    fun systems(): List<TargetDescriptorResponse> =
        catalog.descriptors().map { it.toResponse() }

    @GetMapping("/{system}")
    @Operation(operationId = "searchCohortTargets")
    fun targets(
        @PathVariable system: TargetSystem,
        @RequestParam(required = false) query: String?,
    ): List<ExternalTargetResponse> =
        catalog.search(system, query).map { it.toResponse() }
}

@Schema(name = "TargetDescriptor")
data class TargetDescriptorResponse(
    val system: TargetSystem,
    val kind: CohortKind,
    val systemLabel: String,
    val targetLabel: String,
    val idLabel: String,
    val folderLabel: String?,
    val capabilities: Set<TargetCapability>,
)

@Schema(name = "ExternalTarget")
data class ExternalTargetResponse(
    val system: TargetSystem,
    val externalId: String,
    val kind: CohortKind,
    val label: String,
    val folderLabel: String?,
    val memberCount: Long?,
    val linkedCohortId: Long?,
)

private fun TargetDescriptor.toResponse() =
    TargetDescriptorResponse(system, kind, systemLabel, targetLabel, idLabel, folderLabel, capabilities)

private fun ExternalTarget.toResponse() =
    ExternalTargetResponse(system, externalId, kind, label, folderLabel, memberCount, linkedCohortId)
