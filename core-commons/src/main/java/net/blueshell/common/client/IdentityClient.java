package net.blueshell.common.client;

import net.blueshell.common.identity.SharedUserDetails;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "API", path = "/api/user-details")
public interface IdentityClient {
    SharedUserDetails getIdentity(@RequestParam String token);
}
