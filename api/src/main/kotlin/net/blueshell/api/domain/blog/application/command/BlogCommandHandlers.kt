package net.blueshell.api.domain.blog.application.command

import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.command.*
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.domain.blog.web.mapping.response.sanitizeBlogHtml
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateBlogHandler(
    private val service: BlogService
) : CommandHandler<CreateBlogCommand, Blog> {
    override val commandType = CreateBlogCommand::class

    override fun handle(command: CreateBlogCommand): Blog {
        val blog = Blog(
            title = command.title,
            html = sanitizeBlogHtml(command.html),
            publishedAt = command.publishedAt,
        )
        return service.create(blog)
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
        blog.html = sanitizeBlogHtml(command.html)
        blog.publishedAt = command.publishedAt
        blog.version = command.version
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
