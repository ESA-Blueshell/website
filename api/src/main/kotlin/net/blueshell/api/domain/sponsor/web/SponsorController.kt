package net.blueshell.api.domain.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.sponsor.command.*
import net.blueshell.api.domain.sponsor.web.dto.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.SponsorResponse
import net.blueshell.api.domain.sponsor.web.dto.UpdateSponsorRequest
import net.blueshell.api.domain.sponsor.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Sponsors")
class SponsorController(
    service: net.blueshell.api.domain.sponsor.application.SponsorService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.sponsor.application.SponsorService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    fun findSponsors(): MutableList<SponsorResponse> {
        return commandBus.dispatch(FindSponsorsCommand()).map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody request: CreateSponsorRequest): SponsorResponse {
        val sponsor = commandBus.dispatch(
            CreateSponsorCommand(
                name = requireNotNull(request.name) { "Name is required" },
                description = requireNotNull(request.description) { "Description is required" }
            )
        )
        return sponsor.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(@PathVariable id: Long, @RequestBody request: UpdateSponsorRequest): SponsorResponse {
        val sponsor = commandBus.dispatch(
            UpdateSponsorCommand(
                id = id,
                name = requireNotNull(request.name) { "Name is required" },
                description = requireNotNull(request.description) { "Description is required" },
                version = request.version
            )
        )
        return sponsor.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable id: Long): SponsorResponse {
        return commandBus.dispatch(FindSponsorByIdCommand(id)).asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteSponsorByIdCommand(id))
    }
}
