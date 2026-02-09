package net.blueshell.api.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.web.mapper.BlogMapper
import net.blueshell.api.blog.application.BlogService
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Blogs")
class BlogController(blogService: BlogService, blogMapper: BlogMapper) :
    BaseController<BlogService, BlogMapper>(blogService, blogMapper) {
    @PostMapping("/blogs")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBlog(@Valid @RequestBody dto: BlogDTO): BlogDTO {
        var blog = mapper.fromDTO(dto)
        blog = service.create(blog)
        return mapper.toDTO(blog)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody dto: BlogDTO): BlogDTO {
        var blog = service.findById(id)
        mapper.fromDTO(dto, blog)
        blog = service.update(blog)
        return mapper.toDTO(blog)
    }

    @GetMapping("/blogs")
    @PermitAll
    fun findBlogs(): MutableList<BlogDTO> {
        return mapper.toDTOs(service.findAll())
    }

    @GetMapping("/blogs/{id}")
    @PermitAll
    fun findBlogById(@PathVariable id: Long): BlogDTO {
        return mapper.toDTO(service.findById(id))
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
