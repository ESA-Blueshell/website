package net.blueshell.api.controller.contribution

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.base.BaseController
import net.blueshell.api.dto.contribution.ContributionPeriodDTO
import net.blueshell.api.mapper.contribution.ContributionPeriodMapper
import net.blueshell.api.service.contribution.ContributionPeriodService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionPeriods")
class ContributionPeriodController @Autowired constructor(
    service: ContributionPeriodService,
    mapper: ContributionPeriodMapper
) : BaseController<ContributionPeriodService, ContributionPeriodMapper>(service, mapper) {
    @GetMapping("/contributionPeriods")
    @PermitAll
    fun findContributionPeriods(): MutableList<ContributionPeriodDTO?>? {
        return mapper.toDTOs(service.findAll())
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    fun findCurrentContributionPeriod(): ContributionPeriodDTO? {
        val contributionPeriod = service.findLatest()
        return mapper.toDTO(contributionPeriod)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody dto: @Valid ContributionPeriodDTO?): ContributionPeriodDTO? {
        var contributionPeriod = mapper.fromDTO(dto)
        contributionPeriod = service.create(contributionPeriod)
        return mapper.toDTO(contributionPeriod)
    }

    @PreAuthorize("hasAuthority('BOARD') && #dto.id == #id")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable("id") id: Long?,
        @Valid @RequestBody dto: @Valid ContributionPeriodDTO?
    ): ContributionPeriodDTO? {
        var contributionPeriod = service.findById(id)
        mapper.fromDTO(dto, contributionPeriod)
        contributionPeriod = service.update(contributionPeriod)
        return mapper.toDTO(contributionPeriod)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable("id") id: Long?) {
        service.deleteById(id)
    }
}
