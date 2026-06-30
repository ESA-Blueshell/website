package net.blueshell.api.platform.integration.cohort.adapter.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.platform.integration.cohort.application.TargetCatalog
import net.blueshell.api.platform.integration.cohort.port.out.ExternalTarget
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
    fun systems(): List<TargetDescriptor> = catalog.descriptors()

    @GetMapping("/{system}")
    @Operation(operationId = "searchCohortTargets")
    fun targets(
        @PathVariable system: TargetSystem,
        @RequestParam(required = false) query: String?,
    ): List<ExternalTarget> = catalog.search(system, query)
}
