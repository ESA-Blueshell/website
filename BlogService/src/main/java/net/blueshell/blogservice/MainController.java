package net.blueshell.blogservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @GetMapping("/")
    public String SayHello() {
        return "Blog Service";
    }

    @PostMapping("/")
    public String ReceivePost(@RequestBody String body) {
        return "Received: " + body;
    }
}
