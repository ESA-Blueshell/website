package net.blueshell.api.factory.blog.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.blog.persistence.Blog
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class BlogFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        title: String = "Blog ${System.currentTimeMillis()}",
        html: String = "<p>Content</p>",
        publishedAt: Instant = Instant.now()
    ): Blog {
        return Blog(title = title, html = html, publishedAt = publishedAt)
    }

    fun create(
        title: String = "Blog ${System.currentTimeMillis()}",
        html: String = "<p>Content</p>",
        publishedAt: Instant = Instant.now()
    ): Blog {
        return persistence.persist(build(title, html, publishedAt))
    }
}
