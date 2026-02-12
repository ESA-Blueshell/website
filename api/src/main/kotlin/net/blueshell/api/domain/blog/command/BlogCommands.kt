package net.blueshell.api.domain.blog.command

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.shared.command.Command
import java.time.Instant

data class CreateBlogCommand(
    val title: String,
    val html: String,
    val publishedAt: Instant
) : Command<Blog>

data class UpdateBlogCommand(
    val id: Long,
    val title: String,
    val html: String,
    val publishedAt: Instant,
    val version: Long?
) : Command<Blog>

class FindBlogsCommand : Command<MutableList<Blog>>

data class FindBlogByIdCommand(
    val id: Long
) : Command<Blog>

data class DeleteBlogByIdCommand(
    val id: Long
) : Command<Unit>
