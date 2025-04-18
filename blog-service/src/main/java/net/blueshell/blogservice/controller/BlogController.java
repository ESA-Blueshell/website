package net.blueshell.blogservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.blogservice.model.Blog;
import net.blueshell.blogservice.mapper.BlogMapper;
import net.blueshell.blogservice.service.BlogService;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.common.identity.UserDetailsProvider;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
public class BlogController extends UserDetailsProvider {


    private final BlogMapper blogMapper;
    private final BlogService blogService;

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper) {
        this.blogMapper = blogMapper;
        this.blogService = blogService;
    }
    @RabbitListener(queues = "${communicators.blogService.name}")
    public void asyncCreateBlog(InternalBlogDTO internalBlogDTO) {
        Blog blog = blogMapper.fromDTO(internalBlogDTO);
        blogService.create(blog);
    }

    @GetMapping("/blogs")
    public List<InternalBlogDTO> findAll() {
        log.info("Principal: {}", getPrincipal());
        return blogMapper.toDTOs(blogService.findAll());
    }

    @GetMapping("/blogs/{id}")
    public InternalBlogDTO findById(@PathVariable UUID id) {
        return blogMapper.toDTO(blogService.findById(id));
    }
}