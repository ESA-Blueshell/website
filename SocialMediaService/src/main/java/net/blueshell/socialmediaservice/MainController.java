package net.blueshell.socialmediaservice;

import net.blueshell.socialmediaservice.data.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @GetMapping("/")
    public String SayHello() {
        return "Social Media Service";
    }
    @GetMapping("/queue")
    public String printQueue() {

        StringBuilder sb = new StringBuilder().append("Social Media Service").append("\n");
        for (java.util.Map.Entry<String, String> entry : Map.hashMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }
}
