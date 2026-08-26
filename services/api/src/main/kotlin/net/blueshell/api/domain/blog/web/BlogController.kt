package net.blueshell.api.domain.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.application.BlogUseCases
import net.blueshell.api.domain.blog.web.dto.request.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.request.UpdateBlogRequest
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse
import net.blueshell.api.domain.blog.web.mapping.response.asResponse
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Blogs")
class BlogController(
    service: BlogService,
    private val useCases: BlogUseCases,
) : BaseController<BlogService>(service) {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @PostMapping("/blogs")
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Blog', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBlog(@Valid @RequestBody request: CreateBlogRequest): BlogResponse {
        val blog = useCases.create(request.title, request.html, request.publishedAt)
        return blog.asResponse(frontendUrl)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasPermission(#id, 'Blog', 'write')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody request: UpdateBlogRequest): BlogResponse {
        val blog = useCases.update(id, request.title, request.html, request.publishedAt, request.version)
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
    @PreAuthorize("hasPermission(#id, 'Blog', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
