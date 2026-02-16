package net.blueshell.api.domain.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.command.*
import net.blueshell.api.domain.sponsor.web.dto.request.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.response.SponsorResponse
import net.blueshell.api.domain.sponsor.web.dto.request.UpdateSponsorRequest
import net.blueshell.api.domain.sponsor.web.mapping.asCommand
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
    service: SponsorService,
    private val commandBus: CommandBus
) : BaseController<SponsorService>(service) {
    @PreAuthorize("hasPermission(null, 'Sponsor', 'read')")
    @GetMapping("/sponsors")
    fun findSponsors(): List<SponsorResponse> {
        return commandBus.dispatch(FindSponsorsCommand()).map { it.asResponse() }
    }

    @PreAuthorize("hasPermission(null, 'Sponsor', 'write')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody request: CreateSponsorRequest): SponsorResponse {
        val sponsor = commandBus.dispatch(request.asCommand())
        return sponsor.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'write')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(@PathVariable id: Long, @Valid @RequestBody request: UpdateSponsorRequest): SponsorResponse {
        val sponsor = commandBus.dispatch(request.asCommand(id))
        return sponsor.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'read')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable id: Long): SponsorResponse {
        return commandBus.dispatch(FindSponsorByIdCommand(id)).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'delete')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteSponsorByIdCommand(id))
    }
}
