package net.blueshell.api.domain.blog.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.blog.command.DeleteBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogsCommand
import net.blueshell.api.domain.blog.web.dto.request.CreateBlogRequest
import net.blueshell.api.domain.blog.web.dto.request.UpdateBlogRequest
import net.blueshell.api.domain.blog.web.dto.response.BlogResponse
import net.blueshell.api.domain.blog.web.mapping.asCommand
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
        val blog = commandBus.dispatch(request.asCommand())
        return blog.asResponse(frontendUrl)
    }

    @PostMapping("/blogs/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    fun updateBlog(@PathVariable id: Long, @Valid @RequestBody request: UpdateBlogRequest): BlogResponse {
        val blog = commandBus.dispatch(request.asCommand(id))
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
