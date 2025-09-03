package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.IdentityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Tag(name = "Health")
public class MainController extends IdentityProvider {
    @GetMapping("/health")
    public Boolean healthCheck() {
        return true;
    }
}
