package net.blueshell.client;

import net.blueshell.common.dto.ContributionPeriodDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "API",
        contextId = "contributionPeriodClient",
        path="/contributionPeriods"
)
public interface ContributionPeriodClient {

    @GetMapping
    List<ContributionPeriodDTO> getContributionPeriods();

    @PostMapping
    ContributionPeriodDTO createContributionPeriod(@RequestBody ContributionPeriodDTO dto) throws Exception;

    @PutMapping("/{id}")
    ContributionPeriodDTO updateContributionPeriod(@PathVariable("id") Long id,
                                                   @RequestBody ContributionPeriodDTO dto);

    @DeleteMapping("/{id}")
    void deleteContributionPeriod(@PathVariable("id") Long id);
}
