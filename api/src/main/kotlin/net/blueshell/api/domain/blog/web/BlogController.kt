package net.blueshell.api.domain.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.blog.command.*
import net.blueshell.api.domain.blog.web.dto.BlogResponse
import net.blueshell.api.domain.blog.web.dto.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.UpdateBlogRequest
import net.blueshell.api.domain.blog.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Blogs")
class BlogController(
    service: net.blueshell.api.domain.blog.application.BlogService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.blog.application.BlogService>(service) {
    @Value($$"${frontend.url}")
    private lateinit var frontendUrl: String

    @PostMapping("/blogs")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createBlog(@Valid @RequestBody request: CreateBlogRequest): BlogResponse {
        val title = requireNotNull(request.title) { "Title is required" }
        val html = requireNotNull(request.html) { "Html is required" }
        val publishedAt = requireNotNull(request.publishedAt) { "PublishedAt is required" }
        val blog = commandBus.dispatch(CreateBlogCommand(title, html, publishedAt))
        return blog.asResponse(frontendUrl)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody request: UpdateBlogRequest): BlogResponse {
        val title = requireNotNull(request.title) { "Title is required" }
        val html = requireNotNull(request.html) { "Html is required" }
        val publishedAt = requireNotNull(request.publishedAt) { "PublishedAt is required" }
        val blog = commandBus.dispatch(
            UpdateBlogCommand(
                id = id,
                title = title,
                html = html,
                publishedAt = publishedAt,
                version = request.version
            )
        )
        return blog.asResponse(frontendUrl)
    }

    @GetMapping("/blogs")
    @PermitAll
    fun findBlogs(): MutableList<BlogResponse> {
        return commandBus.dispatch(FindBlogsCommand()).map { it.asResponse(frontendUrl) }.toMutableList()
    }

    @GetMapping("/blogs/{id}")
    @PermitAll
    fun findBlogById(@PathVariable id: Long): BlogResponse {
        return commandBus.dispatch(FindBlogByIdCommand(id)).asResponse(frontendUrl)
    }

    @DeleteMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteBlogByIdCommand(id))
    }
}
