package net.blueshell.api.sponsor.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.sponsor.web.dto.SponsorDTO
import net.blueshell.api.sponsor.persistence.asDto
import net.blueshell.api.sponsor.web.dto.asEntity
import net.blueshell.api.sponsor.application.SponsorService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Sponsors")
class SponsorController(service: SponsorService) : BaseController<SponsorService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    fun findSponsors(): MutableList<SponsorDTO> {
        return service.findAll().map { it.asDto() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody dto: SponsorDTO): SponsorDTO {
        var sponsor = dto.asEntity()
        sponsor = service.create(sponsor)
        return sponsor.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(@PathVariable id: Long, @RequestBody dto: SponsorDTO): SponsorDTO {
        var sponsor = service.findById(id)
        dto.asEntity(sponsor)
        sponsor = service.update(sponsor)
        return sponsor.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable id: Long): SponsorDTO {
        return service.findById(id).asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
