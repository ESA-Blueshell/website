package net.blueshell.common.client;

import net.blueshell.common.dto.BaseDTO;
import net.blueshell.common.dto.AdvancedCommitteeDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "API",
        contextId = "committeeClient",
        path="/committees"
)
public interface CommitteeClient {

    @GetMapping
    List<? extends BaseDTO> getCommittees(@RequestParam(value = "isMember", required = false) Boolean isMember);

    @PostMapping
    AdvancedCommitteeDTO createCommittee(@RequestBody AdvancedCommitteeDTO dto);

    @PutMapping("/{committeeId}")
    BaseDTO updateCommittee(@PathVariable("committeeId") Long id,
                            @RequestBody AdvancedCommitteeDTO dto);

    @DeleteMapping("/{committeeId}")
    void deleteCommittee(@PathVariable("committeeId") Long id);
}