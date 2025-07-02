package net.blueshell.api.controller;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.IdentityProvider;
import net.blueshell.api.dto.InternalBlogDTO;
import net.blueshell.api.mapper.BlogMapper;
import net.blueshell.api.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
public class BlogController extends IdentityProvider {


    private final BlogMapper blogMapper;
    private final BlogService blogService;

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
        this.blogService = blogService;
    }

    @GetMapping("/blogs")
    public List<InternalBlogDTO> findAll() {
        return blogMapper.toDTOs(blogService.findAll());
    }

    @GetMapping("/blogs/{id}")
    public InternalBlogDTO findById(@PathVariable UUID id) {
        return blogMapper.toDTO(blogService.findById(id));
    }
}