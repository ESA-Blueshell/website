package net.blueshell.common.client;

import net.blueshell.common.dto.SponsorDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "API",
        contextId = "sponsorClient",
        path="/sponsors"
)
public interface SponsorClient {

    @GetMapping
    List<SponsorDTO> getSponsors();

    @PostMapping
    SponsorDTO createSponsor(@RequestBody SponsorDTO dto);

    @PutMapping("/{id}")
    SponsorDTO createOrUpdateSponsor(@PathVariable("id") Long id,
                                     @RequestBody SponsorDTO dto);

    @GetMapping("/{id}")
    SponsorDTO getSponsorById(@PathVariable("id") Long id);

    @DeleteMapping("/{id}")
    void deleteSponsorById(@PathVariable("id") Long id);
}
