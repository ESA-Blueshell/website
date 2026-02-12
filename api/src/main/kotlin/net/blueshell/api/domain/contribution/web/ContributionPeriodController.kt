package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.web.dto.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.UpdateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.mapping.asResponse
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.shared.web.BaseController
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
    fun findContributionPeriods(): MutableList<ContributionPeriodResponse> {
        return service.findAll().map { it.asResponse() }.toMutableList()
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    fun findCurrentContributionPeriod(): ContributionPeriodResponse {
        val contributionPeriod = service.findLatest()
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody request: CreateContributionPeriodRequest): ContributionPeriodResponse {
        var contributionPeriod = request.asEntity()
        contributionPeriod = service.create(contributionPeriod)
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD') && #dto.id == #id")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateContributionPeriodRequest
    ): ContributionPeriodResponse {
        var contributionPeriod = service.findById(id)
        request.asEntity(contributionPeriod)
        contributionPeriod = service.update(contributionPeriod)
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
