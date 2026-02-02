package net.blueshell.api.controller.contribution

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.base.BaseController
import net.blueshell.api.dto.contribution.ContributionDTO
import net.blueshell.api.mapper.contribution.ContributionMapper
import net.blueshell.api.service.contribution.ContributionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Contributions")
class ContributionController @Autowired constructor(service: ContributionService?, mapper: ContributionMapper?) :
    BaseController<ContributionService?, ContributionMapper?>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContribution(@Valid @RequestBody dto: @Valid ContributionDTO?): ContributionDTO? {
        var contribution = mapper!!.fromDTO(dto)
        contribution = service!!.create(contribution)
        return mapper.toDTO(contribution)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributions")
    fun findContributions(@RequestParam(required = false) contributionPeriodId: Long?): MutableList<ContributionDTO?>? {
        val contributions = service!!.findByContributionPeriodId(contributionPeriodId)
        return mapper!!.toDTOs(contributions)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(@PathVariable("id") id: Long?) {
        service!!.deleteById(id)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("contributionPeriods/{periodId}/contributions")
    fun findContributionsByPeriodId(@PathVariable("periodId") periodId: Long?): MutableList<ContributionDTO?>? {
        val contributions = service!!.findByContributionPeriodId(periodId)
        return mapper!!.toDTOs(contributions)
    }
}
