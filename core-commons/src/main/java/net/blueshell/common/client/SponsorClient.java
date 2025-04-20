package net.blueshell.common.client;

import net.blueshell.common.dto.SponsorDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "API")
public interface SponsorClient {

    @GetMapping("/sponsors")
    List<SponsorDTO> getSponsors();

    @PostMapping("/sponsors")
    SponsorDTO createSponsor(@RequestBody SponsorDTO dto);

    @PutMapping("/sponsors/{id}")
    SponsorDTO createOrUpdateSponsor(@PathVariable("id") Long id,
                                     @RequestBody SponsorDTO dto);

    @GetMapping("/sponsors/{id}")
    SponsorDTO getSponsorById(@PathVariable("id") Long id);

    @DeleteMapping("/sponsors/{id}")
    void deleteSponsorById(@PathVariable("id") Long id);
}
