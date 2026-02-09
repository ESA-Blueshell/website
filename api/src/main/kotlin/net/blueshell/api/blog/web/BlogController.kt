package net.blueshell.api.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.blog.web.dto.BlogDTO
import net.blueshell.api.blog.web.mapping.asEntity
import net.blueshell.api.blog.web.mapping.asDto
import net.blueshell.api.blog.application.BlogService
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Blogs")
class BlogController(blogService: BlogService) : BaseController<BlogService>(blogService) {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @PostMapping("/blogs")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBlog(@Valid @RequestBody dto: BlogDTO): BlogDTO {
        var blog = dto.asEntity()
        blog = service.create(blog)
        return blog.asDto(frontendUrl)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody dto: BlogDTO): BlogDTO {
        var blog = service.findById(id)
        dto.asEntity(blog)
        blog = service.update(blog)
        return blog.asDto(frontendUrl)
    }

    @GetMapping("/blogs")
    @PermitAll
    fun findBlogs(): MutableList<BlogDTO> {
        return service.findAll().map { it.asDto(frontendUrl) }.toMutableList()
    }

    @GetMapping("/blogs/{id}")
    @PermitAll
    fun findBlogById(@PathVariable id: Long): BlogDTO {
        return service.findById(id).asDto(frontendUrl)
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
