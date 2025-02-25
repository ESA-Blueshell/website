package io.github.jakstepn.eventparser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @GetMapping("/")
    public String SayHello() {
        return "Event Parser";
    }
}
