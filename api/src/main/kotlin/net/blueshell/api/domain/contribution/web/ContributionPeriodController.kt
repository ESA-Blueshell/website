package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.response.ContributionPeriodResponse
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.request.UpdateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.mapping.asCommand
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

    @PreAuthorize("hasPermission(null, 'ContributionPeriod', 'create')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    fun createContributionPeriod(@Valid @RequestBody request: CreateContributionPeriodRequest): ContributionPeriodResponse {
        val contributionPeriod = commandBus.dispatch(request.asCommand())
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'ContributionPeriod', 'write')")
    @PutMapping("/contributionPeriods/{id}")
    fun updateContributionPeriod(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateContributionPeriodRequest
    ): ContributionPeriodResponse {
        val contributionPeriod = commandBus.dispatch(request.asCommand(id))
        return contributionPeriod.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'ContributionPeriod', 'delete')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContributionPeriodById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteContributionPeriodByIdCommand(id))
    }
}
