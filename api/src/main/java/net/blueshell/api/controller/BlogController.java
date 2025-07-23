package net.blueshell.api.controller;

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
import java.util.UUID;

@Slf4j
@RestController
public class BlogController extends BaseController<BlogService, BlogMapper> {

    @Autowired
    public BlogController(BlogService blogService, BlogMapper blogMapper) {
        super(blogService, blogMapper);
    }

    @PostMapping("/blogs")
    @PreAuthorize("hasAuthority('BOARD')")
    public BlogDTO create(@Valid @RequestBody BlogDTO dto) {
        var blog = mapper.fromDTO(dto);
        service.create(blog);
        return mapper.toDTO(blog);
    }

    @GetMapping("/blogs")
    public List<BlogDTO> findAll() {
        return mapper.toDTOs(service.findAll());
    }

    @GetMapping("/blogs/{id}")
    public BlogDTO findById(@PathVariable UUID id) {
        return mapper.toDTO(service.findById(id));
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(@PathVariable UUID id) {
        service.delete(id);
    }
}