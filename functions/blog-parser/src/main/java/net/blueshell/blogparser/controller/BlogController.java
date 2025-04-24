package net.blueshell.blogparser.controller;

import net.blueshell.blogparser.mapper.InternalBlogMapper;
import net.blueshell.client.BlogClient;
import net.blueshell.dto.BlogDTO;
import net.blueshell.dto.InternalBlogDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BlogController {

    private final InternalBlogMapper mapper;
    private final BlogClient blogClient;

    @Autowired
    public BlogController(InternalBlogMapper mapper, BlogClient blogClient) {
        this.mapper = mapper;
        this.blogClient = blogClient;
    }

    @GetMapping("/blogs")
    public List<BlogDTO> findAll() {
        List<InternalBlogDTO> internalBlogs = blogClient.findAll();
        return mapper.fromInternals(internalBlogs);
    }

    @GetMapping("/blogs/{id}")
    public BlogDTO findById(@PathVariable UUID id) {
        InternalBlogDTO internalBlog = blogClient.findById(id);
        return mapper.fromInternal(internalBlog);
    }
}