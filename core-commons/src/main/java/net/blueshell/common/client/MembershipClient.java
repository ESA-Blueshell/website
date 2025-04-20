package net.blueshell.common.client;

import net.blueshell.common.dto.MembershipDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "API")
public interface MembershipClient {

    @GetMapping("/memberships")
    List<MembershipDTO> getMemberships();

    @PostMapping("/memberships")
    MembershipDTO createMembership(@RequestBody MembershipDTO dto);

    @PutMapping("/memberships/{id}")
    MembershipDTO updateMembership(@PathVariable("id") Long id,
                                   @RequestBody MembershipDTO dto);

    @GetMapping("/memberships/{id}")
    MembershipDTO getMembershipById(@PathVariable("id") Long id);
}
