package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.ContributionPeriodDTO;
import net.blueshell.api.mapper.ContributionPeriodMapper;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.service.ContributionPeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "ContributionPeriods")
public class ContributionPeriodController extends BaseController<ContributionPeriodService, ContributionPeriodMapper> {

    @Autowired
    public ContributionPeriodController(ContributionPeriodService service,
                                        ContributionPeriodMapper mapper) {
        super(service, mapper);
    }

    @GetMapping("/contributionPeriods")
    @PermitAll
    public List<ContributionPeriodDTO> findContributionPeriods() {
        return mapper.toDTOs(service.findAll());
    }

    @GetMapping("/contributionPeriods/current")
    @PermitAll
    public ContributionPeriodDTO findCurrentContributionPeriod() {
        var contributionPeriod = service.findLatest();
        return mapper.toDTO(contributionPeriod);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionPeriods")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributionPeriodDTO createContributionPeriod(@Valid @RequestBody ContributionPeriodDTO dto) {
        ContributionPeriod contributionPeriod = mapper.fromDTO(dto);
        contributionPeriod = service.create(contributionPeriod);
        return mapper.toDTO(contributionPeriod);
    }

    @PreAuthorize("hasAuthority('BOARD') && #dto.id == #id")
    @PutMapping("/contributionPeriods/{id}")
    public ContributionPeriodDTO updateContributionPeriod(@PathVariable("id") Long id,
                                                          @Valid @RequestBody ContributionPeriodDTO dto) {
        var contributionPeriod = service.findById(id);
        mapper.fromDTO(dto, contributionPeriod);
        contributionPeriod = service.update(contributionPeriod);
        return mapper.toDTO(contributionPeriod);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributionPeriods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContributionPeriodById(@PathVariable("id") Long id) {
        ContributionPeriod contributionPeriod = service.findById(id);
        service.delete(contributionPeriod);
    }
}
