package net.blueshell.api.feature.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.feature.contribution.dto.ContributionDTO
import net.blueshell.api.feature.contribution.mapper.ContributionMapper
import net.blueshell.api.feature.contribution.model.Contribution
import net.blueshell.api.feature.contribution.service.ContributionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Contributions")
class ContributionController @Autowired constructor(service: ContributionService, mapper: ContributionMapper) :
    BaseController<ContributionService, ContributionMapper>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContribution(@Valid @RequestBody dto: ContributionDTO): ContributionDTO {
        var contribution = mapper.fromDTO(dto)
        contribution = service.create(contribution)
        return mapper.toDTO(contribution)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributions")
    fun findContributions(@RequestParam contributionPeriodId: Long): MutableList<ContributionDTO> {
        val contributions = service.findByContributionPeriodId(contributionPeriodId)
        return mapper.toDTOs(contributions)
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
        return mapper.toDTOs(contributions)
    }
}
