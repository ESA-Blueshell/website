package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.SponsorDTO;
import net.blueshell.api.mapper.SponsorMapper;
import net.blueshell.api.model.Sponsor;
import net.blueshell.api.service.SponsorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class SponsorController extends BaseController<SponsorService, SponsorMapper> {

    private static SponsorService service;
    private static SponsorMapper mapper;

    public SponsorController(SponsorService service, SponsorMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/sponsors")
    public List<Sponsor> getSponsors() {
        return service.findAll();
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
    public Object createOrUpdateSponsor(@PathVariable("id") Long id, @RequestBody SponsorDTO dto) {
        service.findById(id);
        Sponsor sponsor = mapper.fromDTO(dto);
        if (id != null) {
            sponsor.setId(id);
            service.update(sponsor);
        } else {
            service.create(sponsor);
        }

        return mapper.toDTO(sponsor);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = "/sponsors/{id}")
    public SponsorDTO getSponsorById(@PathVariable("id") Long id) {
        return mapper.toDTO(service.findById(id));
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = "/sponsors/{id}")
    public void deleteSponsorById(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
