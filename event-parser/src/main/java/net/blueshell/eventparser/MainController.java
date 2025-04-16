package net.blueshell.eventparser;

import net.blueshell.eventparser.data.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    @GetMapping("/health")
    public boolean healthCheck() {
        return true;
    }

    @GetMapping("/queue")
    public String printQueue() {

        StringBuilder sb = new StringBuilder().append("EventParser ").append("\n");
        for (java.util.Map.Entry<String, String> entry : Map.hashMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }
}
