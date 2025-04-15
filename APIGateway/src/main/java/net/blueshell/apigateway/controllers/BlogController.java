package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.BlogCommunicator;
import net.blueshell.common.dto.BlogDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;

@RestController
@RequestMapping("/blogs")
public class BlogController {

    @Autowired
    private BlogCommunicator communicator;

    @GetMapping("/{id}")
    public BlogDTO getBlog(@PathVariable("id") String id) {
        return communicator.sendSync("/blogs/" + id, HttpMethod.GET, BlogDTO.class);
    }

    @GetMapping("")
    public String getBlogs() {
        return communicator.sendSync("/blogs", HttpMethod.GET, String.class);
    }


    @GetMapping("/queue")
    public String getBlogQueue() {
        return communicator.sendSync("/queue", HttpMethod.GET, String.class);
    }

    @GetMapping("/new")
    public String addToBlogQueue() {
        BlogDTO dto = new BlogDTO();
        dto.setHtml("<html><body><h1>This is a test email</h1></body></html>");
        dto.setPublishedAt(Timestamp.from(Instant.now()));
        System.out.println("Sending blog to email parser service asynchronously");

        return communicator.sendAsync(dto);
    }
}
