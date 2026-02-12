package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.UpdateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionPeriods")
class ContributionPeriodController @Autowired constructor(
    service: ContributionPeriodService,
    private val commandBus: CommandBus
) : BaseController<ContributionPeriodService>(service) {
    @GetMapping("/contributionPeriods")
    @PermitAll
    fun findContributionPeriods(): MutableList<ContributionPeriodResponse> {
        return commandBus.dispatch(FindContributionPeriodsCommand()).map { it.asResponse() }.toMutableList()
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    fun findCurrentContributionPeriod(): ContributionPeriodResponse {
        val contributionPeriod = commandBus.dispatch(FindCurrentContributionPeriodCommand())
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody request: CreateContributionPeriodRequest): ContributionPeriodResponse {
        val contributionPeriod = commandBus.dispatch(
            CreateContributionPeriodCommand(
                startDate = requireNotNull(request.startDate) { "Start date is required" },
                endDate = requireNotNull(request.endDate) { "End date is required" },
                halfYearFee = requireNotNull(request.halfYearFee) { "Half year fee is required" },
                fullYearFee = requireNotNull(request.fullYearFee) { "Full year fee is required" },
                alumniFee = requireNotNull(request.alumniFee) { "Alumni fee is required" },
                listId = request.listId
            )
        )
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateContributionPeriodRequest
    ): ContributionPeriodResponse {
        val contributionPeriod = commandBus.dispatch(
            UpdateContributionPeriodCommand(
                id = id,
                startDate = requireNotNull(request.startDate) { "Start date is required" },
                endDate = requireNotNull(request.endDate) { "End date is required" },
                halfYearFee = requireNotNull(request.halfYearFee) { "Half year fee is required" },
                fullYearFee = requireNotNull(request.fullYearFee) { "Full year fee is required" },
                alumniFee = requireNotNull(request.alumniFee) { "Alumni fee is required" },
                listId = request.listId,
                version = request.version
            )
        )
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteContributionPeriodByIdCommand(id))
    }
}
