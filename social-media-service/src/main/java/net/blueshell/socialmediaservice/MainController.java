package net.blueshell.socialmediaservice;

import net.blueshell.common.dto.SocialDTO;
import net.blueshell.socialmediaservice.data.Map;
import net.blueshell.socialmediaservice.service.SocialMediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    @Autowired
    private SocialMediaService socialMediaService;

    @GetMapping("/health")
    public Boolean checkHealth() {
        return true;
    }

    @PostMapping("/distribute")
    public ResponseEntity<Void> distributeEvent(@RequestBody SocialDTO dto) {
        socialMediaService.distribute(dto);
        return ResponseEntity.ok().build();
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

