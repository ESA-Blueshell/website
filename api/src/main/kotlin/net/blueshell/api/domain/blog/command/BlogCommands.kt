package net.blueshell.api.domain.blog.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.shared.command.Command
import java.time.Instant

data class CreateBlogCommand(
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    val title: String,

    @field:NotBlank(message = "HTML content is required")
    val html: String,

    @field:NotNull(message = "Published date is required")
    val publishedAt: Instant
) : Command<Blog>

data class UpdateBlogCommand(
    @field:NotNull(message = "Blog ID is required")
    val id: Long,

    @field:NotBlank(message = "Title is required")
    @field:Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    val title: String,

    @field:NotBlank(message = "HTML content is required")
    val html: String,

    @field:NotNull(message = "Published date is required")
    val publishedAt: Instant,

    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<Blog>

class FindBlogsCommand : Command<MutableList<Blog>>

data class FindBlogByIdCommand(
    @field:NotNull(message = "Blog ID is required")
    val id: Long
) : Command<Blog>

data class DeleteBlogByIdCommand(
    @field:NotNull(message = "Blog ID is required")
    val id: Long
) : Command<Unit>
