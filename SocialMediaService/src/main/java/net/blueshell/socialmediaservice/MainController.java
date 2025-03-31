package net.blueshell.socialmediaservice;

import net.blueshell.common.Blog;
import net.blueshell.common.Event;
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

    @GetMapping("/")
    public Boolean checkHealth() {
        return true;
    }

    //TODO: remove - debug purposes only
    @PostMapping("/distribute-blog")
    public ResponseEntity<Void> distributeBlog(@RequestBody Blog blog) {
        socialMediaService.distributeBlog(blog);
        return ResponseEntity.ok().build();
    }

    //TODO: remove - debug purposes only
    @PostMapping("/distribute-event")
    public ResponseEntity<Void> distributeEvent(@RequestBody Event event) {
        socialMediaService.distributeEvent(event);
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

