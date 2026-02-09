package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.contribution.web.mapping.asDto
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.contribution.web.mapping.asEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionPeriods")
class ContributionPeriodController @Autowired constructor(
    service: ContributionPeriodService
) : BaseController<ContributionPeriodService>(service) {
    @GetMapping("/contributionPeriods")
    @PermitAll
    fun findContributionPeriods(): MutableList<ContributionPeriodDTO> {
        return service.findAll().map { it.asDto() }.toMutableList()
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    fun findCurrentContributionPeriod(): ContributionPeriodDTO {
        val contributionPeriod = service.findLatest()
        return contributionPeriod.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody dto: ContributionPeriodDTO): ContributionPeriodDTO {
        var contributionPeriod = dto.asEntity()
        contributionPeriod = service.create(contributionPeriod)
        return contributionPeriod.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD') && #dto.id == #id")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable id: Long,
        @Valid @RequestBody dto: ContributionPeriodDTO
    ): ContributionPeriodDTO {
        var contributionPeriod = service.findById(id)
        dto.asEntity(contributionPeriod)
        contributionPeriod = service.update(contributionPeriod)
        return contributionPeriod.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
