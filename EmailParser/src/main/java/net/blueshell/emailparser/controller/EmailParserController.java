package net.blueshell.emailparser.controller;

import net.blueshell.common.DTO.ParsedEmail;
import net.blueshell.emailparser.service.EmailParsingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailParserController {

    @Autowired
    private EmailParsingService parsingService;

    @GetMapping("/")
    public String SayHello() {
        return "Email Parser";
    }

    @PostMapping("/parse-email")
    public ParsedEmail parsedEmail(@RequestBody String newsletterHTML) {
        return parsingService.parseHTML(newsletterHTML);
    }
}
