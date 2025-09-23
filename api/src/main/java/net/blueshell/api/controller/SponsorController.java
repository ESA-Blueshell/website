package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.SponsorDTO;
import net.blueshell.api.mapper.SponsorMapper;
import net.blueshell.api.model.Sponsor;
import net.blueshell.api.service.SponsorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@Tag(name = "Sponsors")
public class SponsorController extends BaseController<SponsorService, SponsorMapper> {

    public SponsorController(SponsorService service, SponsorMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    public List<SponsorDTO> findSponsors() {
        return mapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/sponsors")
    public SponsorDTO createSponsor(@Valid @RequestBody SponsorDTO dto) {
        Sponsor sponsor = mapper.fromDTO(dto);
        service.create(sponsor);
        return mapper.toDTO(sponsor);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = "/sponsors/{id}")
    public SponsorDTO updateSponsor(@PathVariable("id") Long id, @RequestBody SponsorDTO dto) {
        var sponsor = service.findById(id);
        mapper.fromDTO(dto, sponsor);
        service.update(sponsor);
        return mapper.toDTO(sponsor);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = "/sponsors/{id}")
    public SponsorDTO findSponsorById(@PathVariable("id") Long id) {
        return mapper.toDTO(service.findById(id));
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = "/sponsors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSponsorById(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
