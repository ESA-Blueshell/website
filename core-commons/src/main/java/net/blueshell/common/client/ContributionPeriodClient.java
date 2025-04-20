package net.blueshell.common.client;

import net.blueshell.common.dto.ContributionPeriodDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "API")
public interface ContributionPeriodClient {

    @GetMapping("/contributionPeriods")
    List<ContributionPeriodDTO> getContributionPeriods();

    @PostMapping("/contributionPeriods")
    ContributionPeriodDTO createContributionPeriod(@RequestBody ContributionPeriodDTO dto) throws Exception;

    @PutMapping("/contributionPeriods/{id}")
    ContributionPeriodDTO updateContributionPeriod(@PathVariable("id") Long id,
                                                   @RequestBody ContributionPeriodDTO dto);

    @DeleteMapping("/contributionPeriods/{id}")
    void deleteContributionPeriod(@PathVariable("id") Long id);
}
