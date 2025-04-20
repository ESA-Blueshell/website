package net.blueshell.common.client;

import net.blueshell.common.dto.MembershipDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "API",
        contextId = "membershipClient",
        path="/memberships"
)
public interface MembershipClient {

    @GetMapping
    List<MembershipDTO> getMemberships();

    @PostMapping
    MembershipDTO createMembership(@RequestBody MembershipDTO dto);

    @PutMapping("/{id}")
    MembershipDTO updateMembership(@PathVariable("id") Long id,
                                   @RequestBody MembershipDTO dto);

    @GetMapping("/{id}")
    MembershipDTO getMembershipById(@PathVariable("id") Long id);
}
