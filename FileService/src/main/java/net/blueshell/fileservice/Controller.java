package net.blueshell.fileservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    @GetMapping("/")
    public String SayHello() {
        return "Email Parser";
    }
}
