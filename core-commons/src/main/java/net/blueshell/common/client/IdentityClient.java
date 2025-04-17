package net.blueshell.common.client;

import net.blueshell.common.identity.Identity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "API", path = "/api/identity")
public interface IdentityClient {
    Identity getIdentity(@RequestParam String token);
}
