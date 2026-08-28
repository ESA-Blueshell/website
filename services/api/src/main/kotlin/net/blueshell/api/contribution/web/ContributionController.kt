package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.domain.ContributionUseCases
import net.blueshell.api.contribution.domain.toContributionResults
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Contributions")
class ContributionController @Autowired constructor(
    service: ContributionService,
    private val useCases: ContributionUseCases,
) : BaseController<ContributionService>(service) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContribution(@Valid @RequestBody request: CreateContributionRequest): ContributionResponse {
        val contribution = useCases.create(request.userId, request.contributionPeriodId)
        return contribution.asResponse()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'read')")
    @GetMapping("/contributions")
    fun findContributions(@RequestParam contributionPeriodId: Long): List<ContributionResponse> {
        val contributions = service.findByContributionPeriodId(contributionPeriodId).toContributionResults()
        return contributions.map { it.asResponse() }
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'delete')")
    @DeleteMapping("contributionPeriods/{contributionPeriodId}/users/{userId}/contributions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(@PathVariable userId: Long, @PathVariable contributionPeriodId: Long) {
        service.deleteById(Contribution.Id(userId, contributionPeriodId))
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'read')")
    @GetMapping("contributionPeriods/{periodId}/contributions")
    fun findContributionsByPeriodId(@PathVariable periodId: Long): MutableList<ContributionResponse> {
        val contributions = service.findByContributionPeriodId(periodId).toContributionResults()
        return contributions.map { it.asResponse() }.toMutableList()
    }
}
