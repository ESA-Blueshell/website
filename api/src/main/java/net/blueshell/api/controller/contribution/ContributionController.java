package net.blueshell.api.controller.contribution;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.PathParam;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.contribution.ContributionDTO;
import net.blueshell.api.mapper.contribution.ContributionMapper;
import net.blueshell.api.service.contribution.ContributionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Contributions")
public class ContributionController extends BaseController<ContributionService, ContributionMapper> {

    @Autowired
    public ContributionController(ContributionService service, ContributionMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributionDTO createContribution(@Valid @RequestBody ContributionDTO dto) {
        var contribution = mapper.fromDTO(dto);
        contribution = service.create(contribution);
        return mapper.toDTO(contribution);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributions")
    public List<ContributionDTO> findContributions(@RequestParam(required = false) Long contributionPeriodId) {
        var contributions = service.findByContributionPeriodId(contributionPeriodId);
        return mapper.toDTOs(contributions);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping("/contributions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContribution(@PathVariable("id") Long id) {
        var contribution = service.findById(id);
        service.delete(contribution);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("contributionPeriods/{periodId}/contributions")
    public List<ContributionDTO> findContributionsByPeriodId(@PathVariable("periodId") Long periodId) {
        var contributions = service.findByContributionPeriodId(periodId);
        return mapper.toDTOs(contributions);
    }
}
