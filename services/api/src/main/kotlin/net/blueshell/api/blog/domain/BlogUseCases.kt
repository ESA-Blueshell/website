package net.blueshell.api.blog.domain

import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.blog.persistence.BlogRepository
import net.blueshell.api.shared.refusal.Refusal
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Every blog post read and write, straight against the repository. */
@Service
class BlogUseCases(
    private val blogs: BlogRepository,
) {
    @Transactional(readOnly = true)
    fun all(): List<Blog> = blogs.findAll()

    @Transactional(readOnly = true)
    fun byId(id: Long): Blog = blogs.findById(id).orElseThrow { BlogNotFound(id) }

    @Transactional
    fun create(
        title: String,
        html: String,
        publishedAt: Instant,
    ): Blog =
        blogs.saveAndFlush(
            Blog(
                title = title,
                html = sanitizeBlogHtml(html),
                publishedAt = publishedAt,
            ),
        )

    @Transactional
    fun update(
        id: Long,
        title: String,
        html: String,
        publishedAt: Instant,
        version: Long,
    ): Blog {
        val blog = byId(id)
        blog.requireVersion(version)
        blog.title = title
        blog.html = sanitizeBlogHtml(html)
        blog.publishedAt = publishedAt
        return blogs.saveAndFlush(blog)
    }

    @Transactional
    fun remove(id: Long) = blogs.delete(byId(id))
}

class BlogNotFound(
    id: Long,
) : Refusal(HttpStatus.NOT_FOUND, "BlogNotFound", "That blog post does not exist.", mapOf("id" to id))
