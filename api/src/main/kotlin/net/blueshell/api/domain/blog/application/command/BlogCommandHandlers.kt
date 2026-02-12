package net.blueshell.api.domain.blog.application.command

import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.command.CreateBlogCommand
import net.blueshell.api.domain.blog.command.DeleteBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogByIdCommand
import net.blueshell.api.domain.blog.command.FindBlogsCommand
import net.blueshell.api.domain.blog.command.UpdateBlogCommand
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateBlogHandler(
    private val service: BlogService
) : CommandHandler<CreateBlogCommand, Blog> {
    override val commandType = CreateBlogCommand::class

    override fun handle(command: CreateBlogCommand): Blog {
        var blog = Blog()
        blog.title = command.title
        blog.html = command.html
        blog.publishedAt = command.publishedAt
        blog = service.create(blog)
        return blog
    }
}

@Component
class UpdateBlogHandler(
    private val service: BlogService
) : CommandHandler<UpdateBlogCommand, Blog> {
    override val commandType = UpdateBlogCommand::class

    override fun handle(command: UpdateBlogCommand): Blog {
        var blog = service.findById(command.id)
        blog.title = command.title
        blog.html = command.html
        blog.publishedAt = command.publishedAt
        command.version?.let { blog.version = it }
        blog = service.update(blog)
        return blog
    }
}

@Component
class FindBlogsHandler(
    private val service: BlogService
) : CommandHandler<FindBlogsCommand, MutableList<Blog>> {
    override val commandType = FindBlogsCommand::class

    override fun handle(command: FindBlogsCommand): MutableList<Blog> {
        return service.findAll()
    }
}

@Component
class FindBlogByIdHandler(
    private val service: BlogService
) : CommandHandler<FindBlogByIdCommand, Blog> {
    override val commandType = FindBlogByIdCommand::class

    override fun handle(command: FindBlogByIdCommand): Blog {
        return service.findById(command.id)
    }
}

@Component
class DeleteBlogByIdHandler(
    private val service: BlogService
) : CommandHandler<DeleteBlogByIdCommand, Unit> {
    override val commandType = DeleteBlogByIdCommand::class

    override fun handle(command: DeleteBlogByIdCommand) {
        service.deleteById(command.id)
    }
}
