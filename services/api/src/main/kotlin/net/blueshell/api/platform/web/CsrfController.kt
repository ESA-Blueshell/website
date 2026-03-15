package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Security")
class CsrfController {

    @GetMapping("/csrf")
    @PermitAll
    fun csrf(csrfToken: CsrfToken): Map<String, String> {
        return mapOf("token" to csrfToken.token)
    }
}
