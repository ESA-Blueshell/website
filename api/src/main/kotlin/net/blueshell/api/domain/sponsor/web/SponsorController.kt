package net.blueshell.api.domain.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.web.dto.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.SponsorResponse
import net.blueshell.api.domain.sponsor.web.dto.UpdateSponsorRequest
import net.blueshell.api.domain.sponsor.web.mapping.asEntity
import net.blueshell.api.domain.sponsor.web.mapping.asResponse
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Sponsors")
class SponsorController(service: SponsorService) : BaseController<SponsorService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    fun findSponsors(): MutableList<SponsorResponse> {
        return service.findAll().map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody request: CreateSponsorRequest): SponsorResponse {
        var sponsor = request.asEntity()
        sponsor = service.create(sponsor)
        return sponsor.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(@PathVariable id: Long, @RequestBody request: UpdateSponsorRequest): SponsorResponse {
        var sponsor = service.findById(id)
        sponsor = request.asEntity(sponsor)
        sponsor = service.update(sponsor)
        return sponsor.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable id: Long): SponsorResponse {
        return service.findById(id).asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
