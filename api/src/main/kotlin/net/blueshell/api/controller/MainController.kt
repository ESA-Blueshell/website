package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.IdentityProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
@Tag(name = "Health")
class MainController : IdentityProvider() {
    @GetMapping("/health")
    @PermitAll
    fun healthCheck(): Boolean {
        return true
    }
}
