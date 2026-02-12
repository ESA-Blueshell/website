package net.blueshell.api.domain.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.web.dto.BlogResponse
import net.blueshell.api.domain.blog.web.dto.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.UpdateBlogRequest
import net.blueshell.api.domain.blog.web.mapping.asEntity
import net.blueshell.api.domain.blog.web.mapping.asResponse
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
    fun createBlog(@Valid @RequestBody request: CreateBlogRequest): BlogResponse {
        var blog = request.asEntity()
        blog = service.create(blog)
        return blog.asResponse(frontendUrl)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody request: UpdateBlogRequest): BlogResponse {
        var blog = service.findById(id)
        request.asEntity(blog)
        blog = service.update(blog)
        return blog.asResponse(frontendUrl)
    }

    @GetMapping("/blogs")
    @PermitAll
    fun findBlogs(): MutableList<BlogResponse> {
        return service.findAll().map { it.asResponse(frontendUrl) }.toMutableList()
    }

    @GetMapping("/blogs/{id}")
    @PermitAll
    fun findBlogById(@PathVariable id: Long): BlogResponse {
        return service.findById(id).asResponse(frontendUrl)
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
