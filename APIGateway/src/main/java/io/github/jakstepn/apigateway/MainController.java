package io.github.jakstepn.apigateway;

import io.github.jakstepn.common.TestClass;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    @RequestMapping("/")
    public String home() {
        return TestClass.Test;
    }
}
