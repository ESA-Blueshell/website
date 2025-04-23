package net.blueshell.common.client;

import net.blueshell.common.dto.ContributionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Stream;

@FeignClient(
        name = "API",
        contextId = "contributionClient"
)
public interface ContributionClient {

    @PostMapping("/contributions")
    ContributionDTO create(@RequestBody ContributionDTO dto) throws Exception;

    @PutMapping("/contributions/{id}/paid")
    ContributionDTO paid(@PathVariable("id") Long id,
                         @RequestParam("paid") boolean paid) throws Exception;

    @GetMapping("/contributions")
    Stream<ContributionDTO> getAll(@RequestParam(value = "contributionPeriodId", required = false) Long contributionPeriodId);

    @DeleteMapping("/contributions/{id}")
    void delete(@PathVariable("id") Long id) throws Exception;

    @PutMapping("/contributionPeriods/{periodId}/contributions/remind")
    void sendContributionReminder(@PathVariable("periodId") Long periodId) throws Exception;

    @GetMapping("/contributionPeriods/{periodId}/contributions")
    List<ContributionDTO> getContributionsByPeriodId(@PathVariable("periodId") Long periodId) throws Exception;
}
