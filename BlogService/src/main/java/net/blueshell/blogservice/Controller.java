package net.blueshell.blogservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller extends BaseController {

    public Controller(Object service, Object mapper) {
        super(service, mapper);
    }

    @GetMapping("/")
    public String SayHello() {
        return "Blog Service";
    }
}
