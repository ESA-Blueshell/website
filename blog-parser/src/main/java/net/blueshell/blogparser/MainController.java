package net.blueshell.blogparser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @GetMapping("/health")
    public Boolean healthCheck() {
        return true;
    }
}
