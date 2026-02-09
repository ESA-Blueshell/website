package net.blueshell.api.blog.persistence

import net.blueshell.api.blog.persistence.Blog
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BlogModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val blog = blogFactory.createBasic()
            blog.title = unique("blog")
            blog.html = "<p>${unique("html")}</p>"
            blog.publishedAt = timestamp()

            val found = persistAndReload(blog, Blog::class.java) { it.id }

            assertEquals(blog.title, found.title)
            assertEquals(blog.html, found.html)
            assertEquals(blog.publishedAt, found.publishedAt)
        }
    }
}
