package net.blueshell.api.domain.blog.command

import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.dto.BlogDTO
import net.blueshell.api.shared.command.Command

data class CreateBlogCommand(
    val dto: BlogDTO
) : Command<Blog>

data class UpdateBlogCommand(
    val id: Long,
    val dto: BlogDTO
) : Command<Blog>

class FindBlogsCommand : Command<MutableList<Blog>>

data class FindBlogByIdCommand(
    val id: Long
) : Command<Blog>

data class DeleteBlogByIdCommand(
    val id: Long
) : Command<Unit>
