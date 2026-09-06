package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.domain.ContributionPeriodUseCases
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionPeriods")
class ContributionPeriodController @Autowired constructor(
    service: ContributionPeriodService,
    private val useCases: ContributionPeriodUseCases,
) : BaseController<ContributionPeriodService>(service) {
    @GetMapping("/contributionPeriods")
    @PermitAll
    fun findContributionPeriods(): List<ContributionPeriodResponse> {
        return service.findAll().map { it.asResponse() }
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    fun findCurrentContributionPeriod(): ResponseEntity<ContributionPeriodResponse> {
        val contributionPeriod = service.findLatest()
        return if (contributionPeriod == null) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.ok(contributionPeriod.asResponse())
        }
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'ContributionPeriod', 'write')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody request: CreateContributionPeriodRequest): ContributionPeriodResponse {
        val contributionPeriod = useCases.create(
            startDate = request.startDate,
            endDate = request.endDate,
            halfYearCutoffDate = request.halfYearCutoffDate,
            halfYearFee = request.halfYearFee,
            fullYearFee = request.fullYearFee,
            alumniFee = request.alumniFee,
            contactListId = request.contactListId,
        )
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'ContributionPeriod', 'write')")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateContributionPeriodRequest
    ): ContributionPeriodResponse {
        val contributionPeriod = useCases.update(
            id = id,
            startDate = request.startDate,
            endDate = request.endDate,
            halfYearCutoffDate = request.halfYearCutoffDate,
            halfYearFee = request.halfYearFee,
            fullYearFee = request.fullYearFee,
            alumniFee = request.alumniFee,
            contactListId = request.contactListId,
            version = request.version,
        )
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'ContributionPeriod', 'delete')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
