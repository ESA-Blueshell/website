package net.blueshell.socialmediaservice.controller;

import net.blueshell.common.Blog;
import net.blueshell.common.Event;
import net.blueshell.socialmediaservice.service.SocialMediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SocialMediaController {
    @Autowired
    private SocialMediaService socialMediaService;

    @GetMapping("/health")
    public Boolean checkHealth() {
        return true;
    }

    @PostMapping("/distribute-blog")
    public ResponseEntity<Void> distributeBlog(@RequestBody Blog blog) {
        socialMediaService.distributeBlog(blog);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/distribute-event")
    public ResponseEntity<Void> distributeEvent(@RequestBody Event event) {
        socialMediaService.distributeEvent(event);
        return ResponseEntity.ok().build();
    }
}
