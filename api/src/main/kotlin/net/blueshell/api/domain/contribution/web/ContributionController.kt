package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.response.ContributionResponse
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionRequest
import net.blueshell.api.domain.contribution.web.mapping.asCommand
import net.blueshell.api.domain.contribution.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Contributions")
class ContributionController @Autowired constructor(
    service: ContributionService,
    private val commandBus: CommandBus
) : BaseController<ContributionService>(service) {
    @PreAuthorize("hasPermission(null, 'Contribution', 'create')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContribution(@Valid @RequestBody request: CreateContributionRequest): ContributionResponse {
        val contribution = commandBus.dispatch(request.asCommand())
        return contribution.asResponse()
    }

    @PreAuthorize("hasPermission(null, 'Contribution', 'write')")
    @GetMapping("/contributions")
    fun findContributions(@RequestParam contributionPeriodId: Long): MutableList<ContributionResponse> {
        val contributions = commandBus.dispatch(FindContributionsCommand(contributionPeriodId))
        return contributions.map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission(null, 'Contribution', 'delete')")
    @DeleteMapping("contributionPeriods/{contributionPeriodId}/users/{userId}/contributions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(@PathVariable userId: Long, @PathVariable contributionPeriodId: Long) {
        commandBus.dispatch(DeleteContributionCommand(userId, contributionPeriodId))
    }

    @PreAuthorize("hasPermission(null, 'Contribution', 'write')")
    @GetMapping("contributionPeriods/{periodId}/contributions")
    fun findContributionsByPeriodId(@PathVariable periodId: Long): MutableList<ContributionResponse> {
        val contributions = commandBus.dispatch(FindContributionsByPeriodIdCommand(periodId))
        return contributions.map { it.asResponse() }.toMutableList()
    }
}
