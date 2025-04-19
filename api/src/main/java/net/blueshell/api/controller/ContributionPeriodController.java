package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.ContributionPeriodDTO;
import net.blueshell.api.mapper.ContributionPeriodMapper;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.service.ContributionPeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sendinblue.ApiException;

import java.util.List;

@RestController
@RequestMapping("/contributionPeriods")
public class ContributionPeriodController extends BaseController<ContributionPeriodService, ContributionPeriodMapper> {

    @Autowired
    public ContributionPeriodController(ContributionPeriodService service,
                                        ContributionPeriodMapper mapper) {
        super(service, mapper);
    }

    @GetMapping
    public List<ContributionPeriodDTO> getContributionPeriods() {
        return mapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping
    public ContributionPeriodDTO createContributionPeriod(@Valid @RequestBody ContributionPeriodDTO dto) throws ApiException {
        ContributionPeriod contributionPeriod = mapper.fromDTO(dto);
        service.createContributionPeriod(contributionPeriod);
        return mapper.toDTO(contributionPeriod);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping("/{id}")
    public ContributionPeriodDTO updateContributionPeriod(@PathVariable("id") Long id,
                                                                          @Valid @RequestBody ContributionPeriodDTO dto) {
        dto.setId(id);
        ContributionPeriod contributionPeriod = mapper.fromDTO(dto);
        service.update(contributionPeriod);
        return mapper.toDTO(contributionPeriod);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/{id}")
    public void deleteContributionPeriod(@PathVariable("id") Long id) {
        ContributionPeriod contributionPeriod = service.findById(id);
        service.deleteContributionPeriod(contributionPeriod);
    }
}
