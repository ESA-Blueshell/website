package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.web.dto.ContributionDTO
import net.blueshell.api.domain.contribution.web.mapping.asDto
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Contributions")
class ContributionController @Autowired constructor(service: ContributionService) :
    BaseController<ContributionService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContribution(@Valid @RequestBody dto: ContributionDTO): ContributionDTO {
        var contribution = dto.asEntity()
        contribution = service.create(contribution)
        return contribution.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributions")
    fun findContributions(@RequestParam contributionPeriodId: Long): MutableList<ContributionDTO> {
        val contributions = service.findByContributionPeriodId(contributionPeriodId)
        return contributions.map { it.asDto() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("contributionPeriods/{contributionPeriodId}/users/{userId}/contributions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(@PathVariable userId: Long, @PathVariable contributionPeriodId: Long) {
        service.deleteById(Contribution.Id(userId, contributionPeriodId))
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("contributionPeriods/{periodId}/contributions")
    fun findContributionsByPeriodId(@PathVariable periodId: Long): MutableList<ContributionDTO> {
        val contributions = service.findByContributionPeriodId(periodId)
        return contributions.map { it.asDto() }.toMutableList()
    }
}
