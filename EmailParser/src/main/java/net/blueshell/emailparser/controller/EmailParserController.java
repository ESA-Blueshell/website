package net.blueshell.emailparser.controller;

import net.blueshell.common.ParsedEmail;
import net.blueshell.emailparser.data.Map;
import net.blueshell.emailparser.service.EmailParsingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailParserController {

    @Autowired
    private EmailParsingService parsingService;

    @GetMapping("/health")
    public Boolean healthCheck() {
        return true;
    }

    @PostMapping("/parse-email")
    public ParsedEmail parsedEmail(@RequestBody String newsletterHTML) {
        return parsingService.parseHTML(newsletterHTML);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> addQueue() {

        StringBuilder sb = new StringBuilder().append("EmailParser ").append("\n");
        for (java.util.Map.Entry<String, String> entry : Map.hashMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return new ResponseEntity<>(sb.toString(), HttpStatusCode.valueOf(200));
    }
}
