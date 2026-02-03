package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.base.BaseController
import net.blueshell.api.dto.SponsorDTO
import net.blueshell.api.mapper.SponsorMapper
import net.blueshell.api.service.SponsorService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Sponsors")
class SponsorController(service: SponsorService, mapper: SponsorMapper) :
    BaseController<SponsorService, SponsorMapper>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    fun findSponsors(): MutableList<SponsorDTO> {
        return mapper.toDTOs(service.findAll())
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/sponsors")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSponsor(@Valid @RequestBody dto: SponsorDTO): SponsorDTO {
        var sponsor = mapper.fromDTO(dto)
        sponsor = service.create(sponsor)
        return mapper.toDTO(sponsor)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/sponsors/{id}"])
    fun updateSponsor(@PathVariable("id") id: Long, @RequestBody dto: SponsorDTO): SponsorDTO {
        var sponsor = service.findById(id)
        mapper.fromDTO(dto, sponsor)
        sponsor = service.update(sponsor)
        return mapper.toDTO(sponsor)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = ["/sponsors/{id}"])
    fun findSponsorById(@PathVariable("id") id: Long): SponsorDTO {
        return mapper.toDTO(service.findById(id))
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/sponsors/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSponsorById(@PathVariable("id") id: Long) {
        service.deleteById(id)
    }
}
