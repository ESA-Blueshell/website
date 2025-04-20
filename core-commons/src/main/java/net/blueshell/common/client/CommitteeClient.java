package net.blueshell.common.client;

import net.blueshell.common.dto.BaseDTO;
import net.blueshell.common.dto.AdvancedCommitteeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "API")
public interface CommitteeClient {

    @GetMapping("/committees")
    List<? extends BaseDTO> getCommittees(@RequestParam(value = "isMember", required = false) Boolean isMember);

    @PostMapping("/committees")
    AdvancedCommitteeDTO createCommittee(@RequestBody AdvancedCommitteeDTO dto);

    @PutMapping("/committees/{committeeId}")
    BaseDTO updateCommittee(@PathVariable("committeeId") Long id,
                            @RequestBody AdvancedCommitteeDTO dto);

    @DeleteMapping("/committees/{committeeId}")
    void deleteCommittee(@PathVariable("committeeId") Long id);
}