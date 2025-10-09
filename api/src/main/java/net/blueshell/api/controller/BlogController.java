package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.BlogDTO;
import net.blueshell.api.mapper.BlogMapper;
import net.blueshell.api.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@Tag(name = "Blogs")
public class BlogController extends BaseController<BlogService, BlogMapper> {

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper) {
        super(blogService, blogMapper);
    }

    @PostMapping("/blogs")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogDTO createBlog(@Valid @RequestBody BlogDTO dto) {
        var blog = mapper.fromDTO(dto);
        blog = service.create(blog);
        return mapper.toDTO(blog);
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    public BlogDTO updateBlog(@PathVariable("id") Long id, @Valid @RequestBody BlogDTO dto) {
        var blog = service.findById(id);
        mapper.fromDTO(dto, blog);
        blog = service.update(blog);
        return mapper.toDTO(blog);
    }

    @GetMapping("/blogs")
    @PermitAll
    public List<BlogDTO> findBlogs() {
        return mapper.toDTOs(service.findAll());
    }

    @GetMapping("/blogs/{id}")
    @PermitAll
    public BlogDTO findBlogById(@PathVariable Long id) {
        return mapper.toDTO(service.findById(id));
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable Long id) {
        service.deleteById(id);
    }
}