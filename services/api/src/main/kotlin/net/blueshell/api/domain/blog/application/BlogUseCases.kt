package net.blueshell.api.domain.blog.application

import net.blueshell.api.domain.blog.persistence.Blog
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Write operations on blog posts that orchestrate more than a single repository
 * call. Reads and deletes go straight to [BlogService] from the controller.
 */
@Service
class BlogUseCases(
    private val service: BlogService,
) {
    fun create(title: String, html: String, publishedAt: Instant): Blog =
        service.create(
            Blog(
                title = title,
                html = sanitizeBlogHtml(html),
                publishedAt = publishedAt,
            ),
        )

    fun update(id: Long, title: String, html: String, publishedAt: Instant, version: Long): Blog {
        val blog = service.findById(id)
        blog.title = title
        blog.html = sanitizeBlogHtml(html)
        blog.publishedAt = publishedAt
        blog.version = version
        return service.update(blog)
    }
}
