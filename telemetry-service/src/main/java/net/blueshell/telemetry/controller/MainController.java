package net.blueshell.telemetry.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    @GetMapping("/")
    public Boolean checkHealth() {
        return true;
    }
}
