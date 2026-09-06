package net.blueshell.api.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import net.blueshell.api.sponsor.domain.SponsorService
import net.blueshell.api.sponsor.domain.SponsorUseCases
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
@RequestMapping
@Tag(name = "Sponsors")
class SponsorController(
    service: SponsorService,
    private val useCases: SponsorUseCases,
) : BaseController<SponsorService>(service) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Sponsor', 'read')")
    @GetMapping("/sponsors")
    fun findSponsors(): List<SponsorResponse> {
        return service.findAll().map { it.asResponse() }
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Sponsor', 'write')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody request: CreateSponsorRequest): SponsorResponse {
        return useCases.create(request.name, request.description).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'write')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(
        @PathVariable @Positive id: Long,
        @Valid @RequestBody request: UpdateSponsorRequest,
    ): SponsorResponse {
        return useCases.update(id, request.name, request.description, request.version).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'read')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable id: Long): SponsorResponse {
        return service.findById(id).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Sponsor', 'delete')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
